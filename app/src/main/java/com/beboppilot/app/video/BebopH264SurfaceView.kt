package com.beboppilot.app.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.beboppilot.app.sdk.VideoMetrics
import com.beboppilot.app.sdk.VideoStreamState
import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM
import com.parrot.arsdk.arcontroller.ARControllerCodec
import com.parrot.arsdk.arcontroller.ARFrame
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SurfaceView dedicada a decodificar H.264 procedente de ARDeviceControllerStreamListener
 * mediante MediaCodec, basada en H264VideoView del SDKSample oficial de Parrot con manejo
 * seguro de estados de MediaCodec y métricas reales de frames/FPS.
 */
class BebopH264SurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    interface MetricsListener {
        fun onVideoMetricsUpdated(metrics: VideoMetrics)
    }

    private val codecLock = ReentrantLock()
    private val metricsTracker = VideoMetricsTracker()

    private var mediaCodec: MediaCodec? = null
    private var isSurfaceReady = false
    private var isCodecConfigured = false
    private var waitingForKeyFrame = true

    private var spsBytes: ByteArray? = null
    private var ppsBytes: ByteArray? = null
    private var spsDimensions: H264Dimensions? = null

    private var lastMetricsDispatchMs = 0L

    @Volatile
    var metricsListener: MetricsListener? = null

    init {
        holder.addCallback(this)
    }

    fun onStreamStarting() {
        val metrics = codecLock.withLock {
            val hasParams = spsBytes != null && ppsBytes != null
            waitingForKeyFrame = true
            metricsTracker.onStreamStarting(hasCodecParams = hasParams)
            takeMetricsLocked(force = true)
        }
        deliverMetrics(metrics)
    }

    fun onStreamStopped() {
        val metrics = codecLock.withLock {
            releaseCodecLocked()
            spsBytes = null
            ppsBytes = null
            spsDimensions = null
            waitingForKeyFrame = true
            metricsTracker.onStreamStopped()
            takeMetricsLocked(force = true)
        }
        deliverMetrics(metrics)
    }

    fun onFrameTimeout() {
        val metrics = codecLock.withLock {
            metricsTracker.onFrameTimeout()
            takeMetricsLocked(force = true)
        }
        deliverMetrics(metrics)
    }

    fun configureDecoder(codec: ARControllerCodec) {
        val metrics = codecLock.withLock {
            if (codec.type != ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
                Log.w(TAG, "Codec no soportado para video: ${codec.type}")
                metricsTracker.setDecoderError("Codec recibido no es H.264: ${codec.type}")
                return@withLock takeMetricsLocked(force = true)
            }

            val h264 = codec.asH264
            val rawSps = h264.sps?.byteData
            val rawPps = h264.pps?.byteData
            if (rawSps == null || rawSps.isEmpty() || rawPps == null || rawPps.isEmpty()) {
                Log.w(TAG, "SPS/PPS vacíos en configureDecoder")
                metricsTracker.setDecoderError("SPS/PPS vacíos en ARControllerCodec")
                return@withLock takeMetricsLocked(force = true)
            }

            val normalizedSps = H264AnnexBConverter.ensureAnnexBPrefix(rawSps)
            val normalizedPps = H264AnnexBConverter.ensureAnnexBPrefix(rawPps)
            val parsedSps = H264AnnexBConverter.parseSpsDimensions(normalizedSps)

            val configUnchanged = spsBytes?.contentEquals(normalizedSps) == true &&
                ppsBytes?.contentEquals(normalizedPps) == true

            if (configUnchanged && isCodecConfigured && mediaCodec != null &&
                metricsTracker.snapshot().decoderError == null
            ) {
                spsDimensions = parsedSps
                metricsTracker.setSpsDimensions(parsedSps?.width, parsedSps?.height, resetOutput = false)
                Log.d(
                    TAG,
                    "configureDecoder H.264 sin cambios (SPS=${normalizedSps.size} B, PPS=${normalizedPps.size} B, dims=${parsedSps?.width}x${parsedSps?.height}); manteniendo MediaCodec activo",
                )
                return@withLock takeMetricsLocked(force = false)
            }

            spsBytes = normalizedSps
            ppsBytes = normalizedPps
            spsDimensions = parsedSps
            waitingForKeyFrame = true
            metricsTracker.setDecoderError(null)
            metricsTracker.setSpsDimensions(parsedSps?.width, parsedSps?.height, resetOutput = true)
            Log.i(
                TAG,
                "configureDecoder H.264: SPS=${normalizedSps.size} B, PPS=${normalizedPps.size} B, SPS=${parsedSps?.let { "${it.width}x${it.height}" } ?: "N/D"}",
            )

            if (isSurfaceReady) {
                configureCodecLocked()
            } else {
                metricsTracker.setState(VideoStreamState.WAITING_FIRST_FRAME)
            }
            takeMetricsLocked(force = true)
        }
        deliverMetrics(metrics)
    }

    fun displayFrame(frame: ARFrame) {
        val metrics = codecLock.withLock {
            val rawSize = frame.dataSize
            val data = frame.byteData
            if (data == null || rawSize <= 0) {
                return@withLock null
            }
            val safeSize = rawSize.coerceAtMost(data.size)
            val nowMs = SystemClock.elapsedRealtime()
            metricsTracker.recordFrameReceived(bytes = safeSize, missed = frame.missed, nowMs = nowMs)

            val codec = mediaCodec
            if (codec == null || !isCodecConfigured) {
                val waitingState = if (spsBytes == null || ppsBytes == null) {
                    VideoStreamState.WAITING_DECODER
                } else {
                    VideoStreamState.WAITING_FIRST_FRAME
                }
                metricsTracker.setState(waitingState)
                return@withLock takeMetricsLocked(force = false, nowMs = nowMs)
            }

            if (waitingForKeyFrame) {
                if (frame.isIFrame) {
                    waitingForKeyFrame = false
                    metricsTracker.setState(VideoStreamState.RECEIVING)
                    Log.i(TAG, "Primer I-frame recibido ($safeSize bytes)")
                } else {
                    metricsTracker.setState(VideoStreamState.WAITING_FIRST_FRAME)
                    return@withLock takeMetricsLocked(force = false, nowMs = nowMs)
                }
            } else {
                metricsTracker.setState(VideoStreamState.RECEIVING)
            }

            var outputFormatChanged = false
            try {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        if (inputBuffer.remaining() >= safeSize) {
                            inputBuffer.put(data, 0, safeSize)
                            val flags = if (frame.isIFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            codec.queueInputBuffer(inputIndex, 0, safeSize, 0L, flags)
                        } else {
                            Log.w(TAG, "Frame ($safeSize B) excede capacidad del buffer (${inputBuffer.remaining()} B)")
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, 0)
                        }
                    }
                }

                outputFormatChanged = drainOutputBuffersLocked(codec, nowMs)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error de estado en MediaCodec durante displayFrame", e)
                releaseCodecLocked()
                waitingForKeyFrame = true
                metricsTracker.setDecoderError("MediaCodec IllegalStateException: ${e.message}")
                return@withLock takeMetricsLocked(force = true, nowMs = nowMs)
            }

            takeMetricsLocked(force = outputFormatChanged, nowMs = nowMs)
        }
        deliverMetrics(metrics)
    }

    private fun drainOutputBuffersLocked(codec: MediaCodec, nowMs: Long): Boolean {
        val info = MediaCodec.BufferInfo()
        var decodedThisCall = 0
        var outputFormatChanged = false
        var outIndex = codec.dequeueOutputBuffer(info, 0)
        while (outIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
            when {
                outIndex >= 0 -> {
                    codec.releaseOutputBuffer(outIndex, true)
                    decodedThisCall++
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    val rawW = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                    val rawH = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                    val cropLeft = format.getOptionalInt("crop-left")
                    val cropRight = format.getOptionalInt("crop-right")
                    val cropTop = format.getOptionalInt("crop-top")
                    val cropBottom = format.getOptionalInt("crop-bottom")
                    val resolved = VideoFormatUtils.resolveOutputDimensions(
                        rawWidth = rawW,
                        rawHeight = rawH,
                        cropLeft = cropLeft,
                        cropRight = cropRight,
                        cropTop = cropTop,
                        cropBottom = cropBottom,
                    )
                    metricsTracker.setOutputDimensions(resolved?.width, resolved?.height)
                    outputFormatChanged = true
                    Log.i(
                        TAG,
                        "Formato de salida MediaCodec: ${resolved?.let { "${it.width}x${it.height}" } ?: "N/D"} (raw=${rawW}x${rawH}, crop=$cropLeft,$cropTop-$cropRight,$cropBottom)",
                    )
                }
            }
            outIndex = codec.dequeueOutputBuffer(info, 0)
        }
        if (decodedThisCall > 0) {
            metricsTracker.recordDecodedFrames(decodedThisCall, nowMs)
        }
        return outputFormatChanged
    }

    private fun configureCodecLocked() {
        val sps = spsBytes ?: return
        val pps = ppsBytes ?: return
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            return
        }

        val initWidth = spsDimensions?.width ?: CODEC_INIT_FALLBACK_WIDTH
        val initHeight = spsDimensions?.height ?: CODEC_INIT_FALLBACK_HEIGHT

        try {
            releaseCodecLocked()
            val codec = MediaCodec.createDecoderByType(VIDEO_MIME_TYPE)
            val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, initWidth, initHeight).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            }
            codec.configure(format, surface, null, 0)
            codec.start()
            mediaCodec = codec
            isCodecConfigured = true
            waitingForKeyFrame = true
            metricsTracker.setDecoderError(null)
            metricsTracker.setState(VideoStreamState.WAITING_FIRST_FRAME)
            Log.i(
                TAG,
                "MediaCodec configurado e iniciado (format=${initWidth}x${initHeight}, sps=${spsDimensions?.let { "${it.width}x${it.height}" } ?: "N/D"})",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Fallo al configurar MediaCodec", t)
            releaseCodecLocked()
            metricsTracker.setDecoderError("Fallo al configurar MediaCodec: ${t.message}")
        }
    }

    private fun releaseCodecLocked() {
        val codec = mediaCodec ?: return
        mediaCodec = null
        val wasConfigured = isCodecConfigured
        isCodecConfigured = false
        metricsTracker.setOutputDimensions(null, null)
        try {
            if (wasConfigured) {
                codec.stop()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error deteniendo MediaCodec", t)
        }
        try {
            codec.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Error liberando MediaCodec", t)
        }
    }

    private fun takeMetricsLocked(
        force: Boolean,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): VideoMetrics? {
        if (metricsListener == null) return null
        if (!force && (nowMs - lastMetricsDispatchMs) < METRICS_THROTTLE_MS) {
            return null
        }
        lastMetricsDispatchMs = nowMs
        return metricsTracker.snapshot(nowMs)
    }

    private fun deliverMetrics(metrics: VideoMetrics?) {
        if (metrics != null) {
            metricsListener?.onVideoMetricsUpdated(metrics)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val metrics = codecLock.withLock {
            isSurfaceReady = true
            val hasDecoderError = metricsTracker.snapshot().decoderError != null
            if (spsBytes != null && ppsBytes != null && (!isCodecConfigured || mediaCodec == null || hasDecoderError)) {
                configureCodecLocked()
                takeMetricsLocked(force = true)
            } else {
                null
            }
        }
        deliverMetrics(metrics)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // MediaCodec escala automáticamente hacia el SurfaceView.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val metrics = codecLock.withLock {
            isSurfaceReady = false
            releaseCodecLocked()
            takeMetricsLocked(force = true)
        }
        deliverMetrics(metrics)
    }

    private fun MediaFormat.getOptionalInt(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    companion object {
        private const val TAG = "BebopH264SurfaceView"
        private const val VIDEO_MIME_TYPE = "video/avc"
        private const val DEQUEUE_TIMEOUT_US = 33_000L
        // Dimensiones mínimas solo para inicializar MediaFormat si el parser SPS fallara;
        // nunca se exponen en VideoMetrics como resolución real.
        private const val CODEC_INIT_FALLBACK_WIDTH = 856
        private const val CODEC_INIT_FALLBACK_HEIGHT = 480
        private const val METRICS_THROTTLE_MS = 200L
    }
}
