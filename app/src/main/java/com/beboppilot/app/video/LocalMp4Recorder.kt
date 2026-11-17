package com.beboppilot.app.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.beboppilot.app.sdk.LocalRecordState
import com.beboppilot.app.sdk.LocalRecordingState
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Graba el stream H.264 recibido directamente a un MP4 mediante MediaMuxer sin recodificar
 * ni afectar a BebopH264SurfaceView / MediaCodec.
 */
class LocalMp4Recorder(
    context: Context,
    private val onStateChanged: (LocalRecordingState) -> Unit,
) {

    private val appContext = context.applicationContext
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopPilot-Mp4Muxer")
    }

    private val isActive = AtomicBoolean(false)
    private val queuedFrameTasks = AtomicInteger(0)

    // Accedidos exclusivamente dentro de ioExecutor
    private var spsAnnexB: ByteArray? = null
    private var ppsAnnexB: ByteArray? = null
    private var spsDimensionsKnown = false
    private var videoWidth = DEFAULT_WIDTH
    private var videoHeight = DEFAULT_HEIGHT

    private var mediaMuxer: MediaMuxer? = null
    private var hardwareTranscoder: HardwareMp4Transcoder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null
    private var legacyFile: File? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var waitingKeyFrame = true
    private var waitingSinceMs = 0L
    private var waitingSkippedFrames = 0
    private var startPtsNs = 0L
    private var lastWrittenPtsUs = -1L
    private var writtenFrames = 0L
    private var lastDisplayName: String? = null
    private var lastDurationDispatchMs = 0L
    private var lastEmittedDurationSec = -1L

    fun onCodecConfigured(sps: ByteArray, pps: ByteArray) {
        val spsCopy = H264AnnexBConverter.ensureAnnexBPrefix(sps)
        val ppsCopy = H264AnnexBConverter.ensureAnnexBPrefix(pps)
        val dims = H264AnnexBConverter.parseSpsDimensions(spsCopy)

        ioExecutor.execute {
            val sameParams = spsAnnexB?.contentEquals(spsCopy) == true &&
                ppsAnnexB?.contentEquals(ppsCopy) == true
            if (sameParams) {
                if (dims != null && !spsDimensionsKnown) {
                    videoWidth = dims.width
                    videoHeight = dims.height
                    spsDimensionsKnown = true
                }
                return@execute
            }

            // Si los parámetros SPS/PPS cambian en medio de una grabación activa,
            // cerramos el segmento previo de forma íntegra antes de aceptar el nuevo formato.
            if (isActive.get() && muxerStarted && mediaMuxer != null) {
                Log.i(TAG, "Cambio de SPS/PPS durante grabación MP4; cerrando segmento actual antes de rotar")
                finalizeMuxerLocked(errorText = null, keepActiveForRotation = true)
            }

            spsAnnexB = spsCopy
            ppsAnnexB = ppsCopy
            if (dims != null) {
                videoWidth = dims.width
                videoHeight = dims.height
                spsDimensionsKnown = true
            } else {
                spsDimensionsKnown = false
            }

            if (isActive.get() && mediaMuxer == null) {
                waitingKeyFrame = true
                waitingSinceMs = SystemClock.elapsedRealtime()
                waitingSkippedFrames = 0
                emitState(
                    LocalRecordingState(
                        state = LocalRecordState.WAITING_KEYFRAME,
                        durationSec = 0L,
                        writtenFrames = 0L,
                    )
                )
            }
        }
    }

    fun updateVideoDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        ioExecutor.execute {
            if (!spsDimensionsKnown && !muxerStarted) {
                videoWidth = width
                videoHeight = height
            }
        }
    }

    fun startRecording() {
        if (!isActive.compareAndSet(false, true)) return
        ioExecutor.execute {
            if (!isActive.get()) return@execute
            resetSegmentCountersLocked()
            val hasConfig = spsAnnexB != null && ppsAnnexB != null
            emitState(
                LocalRecordingState(
                    state = if (hasConfig) LocalRecordState.WAITING_KEYFRAME else LocalRecordState.WAITING_CONFIG,
                    durationSec = 0L,
                    writtenFrames = 0L,
                )
            )
        }
    }

    fun onFrameReceived(data: ByteArray, size: Int, isIFrame: Boolean, missedCount: Int) {
        if (!isActive.get()) return
        if (size <= 0 || size > data.size) {
            return
        }
        if (queuedFrameTasks.get() >= MAX_QUEUED_FRAME_TASKS) {
            return
        }
        val frameCopy = data.copyOf(size)
        val captureTimeNs = SystemClock.elapsedRealtimeNanos()
        val captureElapsedMs = SystemClock.elapsedRealtime()

        queuedFrameTasks.incrementAndGet()
        ioExecutor.execute {
            try {
                if (!isActive.get()) return@execute
                if (spsAnnexB == null || ppsAnnexB == null) {
                    return@execute
                }

                val activeTranscoder = hardwareTranscoder
                val isKeySyncFrame: Boolean
                val sanitized: AnnexBSampleResult?
                if (activeTranscoder != null) {
                    sanitized = null
                    isKeySyncFrame = isIFrame
                } else {
                    sanitized = H264AnnexBConverter.sanitizeAnnexBForMediaMuxer(frameCopy, frameCopy.size)
                        ?: return@execute
                    isKeySyncFrame = isIFrame || sanitized.hasIdrSlice
                }

                var markSampleAsKey = isKeySyncFrame
                if (waitingKeyFrame) {
                    val waitedMs = (captureElapsedMs - waitingSinceMs).coerceAtLeast(0L)
                    val acceptCleanKey = isKeySyncFrame && missedCount == 0
                    val acceptRecoveredKey = isKeySyncFrame && (waitedMs >= PARTIAL_KEYFRAME_WAIT_MS || waitingSkippedFrames >= 5)
                    val acceptPirFallback = !isKeySyncFrame &&
                        waitedMs >= PIR_FALLBACK_WAIT_MS &&
                        waitingSkippedFrames >= PIR_FALLBACK_MIN_FRAMES

                    when {
                        acceptCleanKey -> Unit
                        acceptRecoveredKey -> {
                            Log.w(TAG, "Aceptando I-frame con missed=$missedCount tras ${waitedMs}ms")
                        }
                        acceptPirFallback -> {
                            markSampleAsKey = true
                            Log.w(
                                TAG,
                                "Sin I-frame/IDR en ${waitedMs}ms ($waitingSkippedFrames frames; posible PIR); iniciando grabación",
                            )
                        }
                        else -> {
                            waitingSkippedFrames++
                            return@execute
                        }
                    }

                    if (mediaMuxer == null && !prepareMuxerLocked()) {
                        return@execute
                    }
                    waitingKeyFrame = false
                    startPtsNs = captureTimeNs
                    lastWrittenPtsUs = -1L
                    Log.i(TAG, "Grabación MP4 iniciada ($size bytes, key=$isKeySyncFrame, missed=$missedCount, hw=${hardwareTranscoder != null})")
                }

                val muxer = mediaMuxer ?: return@execute
                val rawPtsUs = ((captureTimeNs - startPtsNs) / 1000L).coerceAtLeast(0L)
                val ptsUs = if (rawPtsUs <= lastWrittenPtsUs) lastWrittenPtsUs + 1L else rawPtsUs
                lastWrittenPtsUs = ptsUs

                val transcoder = hardwareTranscoder
                if (transcoder != null) {
                    val ok = transcoder.feedFrame(frameCopy, frameCopy.size, markSampleAsKey, ptsUs)
                    if (!ok) {
                        abortMuxerLocked("Error codificando video MP4 en hardware")
                        return@execute
                    }
                    writtenFrames = transcoder.encodedFramesCount
                    val durationSec = ptsUs / 1_000_000L
                    if (writtenFrames <= 1L ||
                        durationSec != lastEmittedDurationSec ||
                        (captureElapsedMs - lastDurationDispatchMs) >= 500L
                    ) {
                        lastDurationDispatchMs = captureElapsedMs
                        lastEmittedDurationSec = durationSec
                        emitState(
                            LocalRecordingState(
                                state = LocalRecordState.RECORDING,
                                durationSec = durationSec,
                                writtenFrames = writtenFrames,
                            )
                        )
                    }
                } else {
                    if (!muxerStarted || videoTrackIndex < 0) return@execute
                    val sample = sanitized ?: H264AnnexBConverter.sanitizeAnnexBForMediaMuxer(frameCopy, frameCopy.size)
                        ?: return@execute
                    val sampleBuffer = sample.buffer
                    val info = MediaCodec.BufferInfo().apply {
                        offset = 0
                        this.size = sampleBuffer.remaining()
                        presentationTimeUs = ptsUs
                        flags = if (markSampleAsKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    }

                    try {
                        muxer.writeSampleData(videoTrackIndex, sampleBuffer, info)
                        writtenFrames++
                        val durationSec = ptsUs / 1_000_000L
                        if (writtenFrames == 1L ||
                            durationSec != lastEmittedDurationSec ||
                            (captureElapsedMs - lastDurationDispatchMs) >= 500L
                        ) {
                            lastDurationDispatchMs = captureElapsedMs
                            lastEmittedDurationSec = durationSec
                            emitState(
                                LocalRecordingState(
                                    state = LocalRecordState.RECORDING,
                                    durationSec = durationSec,
                                    writtenFrames = writtenFrames,
                                )
                            )
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error escribiendo muestra MP4 en MediaMuxer", t)
                        val msg = "Error grabando MP4: ${t.message ?: t::class.java.simpleName}"
                        abortMuxerLocked(errorText = msg)
                    }
                }
            } finally {
                queuedFrameTasks.decrementAndGet()
            }
        }
    }

    fun stopRecording() {
        if (!isActive.compareAndSet(true, false)) return
        emitState(
            LocalRecordingState(
                state = LocalRecordState.STOPPING,
                durationSec = if (lastWrittenPtsUs > 0) lastWrittenPtsUs / 1_000_000L else 0L,
                writtenFrames = writtenFrames,
            )
        )
        ioExecutor.execute {
            finalizeMuxerLocked(errorText = null, keepActiveForRotation = false)
        }
    }

    fun release() {
        isActive.set(false)
        ioExecutor.execute {
            finalizeMuxerLocked(errorText = null, keepActiveForRotation = false)
        }
        ioExecutor.shutdown()
        runCatching {
            if (!ioExecutor.awaitTermination(RELEASE_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timeout esperando cierre limpio de MediaMuxer al liberar")
            }
        }
    }

    private fun resetSegmentCountersLocked() {
        writtenFrames = 0L
        lastWrittenPtsUs = -1L
        startPtsNs = 0L
        lastDurationDispatchMs = 0L
        lastEmittedDurationSec = -1L
        waitingKeyFrame = true
        waitingSinceMs = SystemClock.elapsedRealtime()
        waitingSkippedFrames = 0
        muxerStarted = false
        videoTrackIndex = -1
    }

    private fun prepareMuxerLocked(): Boolean {
        val sps = spsAnnexB ?: return false
        val pps = ppsAnnexB ?: return false

        return try {
            val nowMs = System.currentTimeMillis()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs))
            val fileName = "BebopPilot_$timestamp.mp4"
            lastDisplayName = fileName

            val resolver = appContext.contentResolver
            val muxer: MediaMuxer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BebopPilot")
                    put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear archivo en MediaStore Movies/BebopPilot")
                val descriptor = resolver.openFileDescriptor(uri, "rw")
                    ?: run {
                        resolver.delete(uri, null, null)
                        throw IllegalStateException("No se pudo abrir FileDescriptor de MediaStore")
                    }
                pendingUri = uri
                pfd = descriptor
                muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, "BebopPilot").apply { mkdirs() }
                val outFile = File(targetDir, fileName)
                legacyFile = outFile
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val transcoder = HardwareMp4Transcoder(
                sourceWidth = videoWidth,
                sourceHeight = videoHeight,
                spsAnnexB = sps,
                ppsAnnexB = pps,
                muxer = muxer,
            )
            if (transcoder.prepare()) {
                hardwareTranscoder = transcoder
                mediaMuxer = muxer
                muxerStarted = false
                Log.i(TAG, "MP4 hardware con IDR/1s iniciado (${transcoder.encodedWidth}x${transcoder.encodedHeight}) -> $fileName")
            } else {
                val format = MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight).apply {
                    setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                    setByteBuffer("csd-1", ByteBuffer.wrap(pps))
                }
                videoTrackIndex = muxer.addTrack(format)
                muxer.start()
                mediaMuxer = muxer
                muxerStarted = true
                Log.i(TAG, "MediaMuxer directo iniciado (${videoWidth}x${videoHeight}) -> $fileName")
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo inicializar MediaMuxer", t)
            abortMuxerLocked("Error iniciando MP4: ${t.message ?: t::class.java.simpleName}")
            false
        }
    }

    private fun abortMuxerLocked(errorText: String) {
        isActive.set(false)
        finalizeMuxerLocked(errorText = errorText, keepActiveForRotation = false)
    }

    private fun finalizeMuxerLocked(errorText: String?, keepActiveForRotation: Boolean) {
        val transcoder = hardwareTranscoder
        val muxer = mediaMuxer
        val descriptor = pfd
        val uri = pendingUri
        val file = legacyFile

        hardwareTranscoder = null
        mediaMuxer = null
        pfd = null
        pendingUri = null
        legacyFile = null
        videoTrackIndex = -1

        var frames = writtenFrames
        var didMuxerStart = muxerStarted
        if (transcoder != null) {
            frames = transcoder.finish().coerceAtLeast(frames)
            didMuxerStart = didMuxerStart || transcoder.isMuxerStarted
            transcoder.release()
        }

        var finalError = errorText
        if (muxer != null) {
            try {
                if (didMuxerStart && frames > 0L) {
                    muxer.stop()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error deteniendo MediaMuxer", t)
                if (finalError == null && frames > 0L) {
                    finalError = "Error al cerrar MP4: ${t.message}"
                }
            }
            try {
                muxer.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Error liberando MediaMuxer", t)
            }
        }
        muxerStarted = false

        try {
            descriptor?.close()
        } catch (_: Throwable) {
        }

        var savedPath: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
            if (frames > 0L && finalError == null) {
                val publish = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                try {
                    val updated = appContext.contentResolver.update(uri, publish, null, null)
                    if (updated <= 0) {
                        Log.w(TAG, "MediaStore update IS_PENDING=0 devolvió $updated para $uri")
                    }
                    savedPath = "Movies/BebopPilot/${lastDisplayName ?: ""}"
                    Log.i(TAG, "MP4 local guardado ($frames frames): $savedPath")
                } catch (t: Throwable) {
                    Log.e(TAG, "Error publicando MP4 en MediaStore", t)
                    finalError = "Error publicando MP4 en galería: ${t.message}"
                }
            } else {
                runCatching { appContext.contentResolver.delete(uri, null, null) }
            }
        } else if (file != null) {
            if (frames > 0L && finalError == null) {
                savedPath = "Movies/BebopPilot/${file.name}"
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DATA, file.absolutePath)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.TITLE, file.nameWithoutExtension)
                    }
                    appContext.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                }
            } else {
                runCatching { file.delete() }
            }
        }

        resetSegmentCountersLocked()
        if (!keepActiveForRotation) {
            emitState(
                LocalRecordingState(
                    state = if (finalError != null) LocalRecordState.ERROR else LocalRecordState.IDLE,
                    durationSec = 0L,
                    writtenFrames = 0L,
                    lastSavedDisplayPath = savedPath,
                    errorText = finalError,
                )
            )
        }
    }

    private fun emitState(state: LocalRecordingState) {
        onStateChanged(state)
    }

    companion object {
        private const val TAG = "LocalMp4Recorder"
        private const val DEFAULT_WIDTH = 856
        private const val DEFAULT_HEIGHT = 480
        private const val PARTIAL_KEYFRAME_WAIT_MS = 450L
        private const val PIR_FALLBACK_WAIT_MS = 650L
        private const val PIR_FALLBACK_MIN_FRAMES = 12
        private const val MAX_QUEUED_FRAME_TASKS = 8
        private const val RELEASE_AWAIT_TIMEOUT_MS = 1_500L
    }
}
