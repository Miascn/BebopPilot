package com.beboppilot.app.video

import android.os.SystemClock
import com.beboppilot.app.sdk.VideoMetrics
import com.beboppilot.app.sdk.VideoStreamState
import kotlin.math.roundToInt

/**
 * Utilidades puras para calcular dimensiones reales de salida de MediaCodec
 * aplicando las claves de recorte (crop-left, crop-right, crop-top, crop-bottom).
 */
object VideoFormatUtils {
    fun resolveOutputDimensions(
        rawWidth: Int,
        rawHeight: Int,
        cropLeft: Int?,
        cropRight: Int?,
        cropTop: Int?,
        cropBottom: Int?,
    ): H264Dimensions? {
        val croppedWidth = if (cropLeft != null && cropRight != null && cropRight >= cropLeft) {
            val candidate = cropRight - cropLeft + 1
            if (candidate > 0 && (rawWidth <= 0 || candidate <= rawWidth)) candidate else rawWidth
        } else {
            rawWidth
        }

        val croppedHeight = if (cropTop != null && cropBottom != null && cropBottom >= cropTop) {
            val candidate = cropBottom - cropTop + 1
            if (candidate > 0 && (rawHeight <= 0 || candidate <= rawHeight)) candidate else rawHeight
        } else {
            rawHeight
        }

        return if (croppedWidth > 0 && croppedHeight > 0) {
            H264Dimensions(croppedWidth, croppedHeight)
        } else {
            null
        }
    }
}

/**
 * Acumulador thread-safe de métricas reales de recepción y decodificación H.264
 * basado en ventanas temporales deslizantes y bytes reales recibidos.
 */
class VideoMetricsTracker(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minRateSpanMs: Long = DEFAULT_MIN_RATE_SPAN_MS,
) {
    private data class RxSample(val timestampMs: Long, val bytes: Int)

    private val lock = Any()

    private var streamState = VideoStreamState.IDLE
    private var spsWidth: Int? = null
    private var spsHeight: Int? = null
    private var outputWidth: Int? = null
    private var outputHeight: Int? = null

    private var totalFramesReceived = 0L
    private var decodedFrames = 0L
    private var missedFrames = 0L
    private var lastFrameTimestampMs = 0L
    private var decoderError: String? = null

    private var rateWindowStartMs = 0L
    private val rxSamples = ArrayDeque<RxSample>()
    private var rxWindowBytes = 0L
    private val decTimestamps = ArrayDeque<Long>()

    fun onStreamStarting(hasCodecParams: Boolean) {
        synchronized(lock) {
            totalFramesReceived = 0L
            decodedFrames = 0L
            missedFrames = 0L
            lastFrameTimestampMs = 0L
            decoderError = null
            outputWidth = null
            outputHeight = null
            if (!hasCodecParams) {
                spsWidth = null
                spsHeight = null
            }
            clearRateWindowsLocked()
            streamState = if (hasCodecParams) {
                VideoStreamState.WAITING_FIRST_FRAME
            } else {
                VideoStreamState.STARTING
            }
        }
    }

    fun onStreamStopped() {
        synchronized(lock) {
            spsWidth = null
            spsHeight = null
            outputWidth = null
            outputHeight = null
            decoderError = null
            clearRateWindowsLocked()
            streamState = VideoStreamState.IDLE
        }
    }

    fun onFrameTimeout() {
        synchronized(lock) {
            clearRateWindowsLocked()
            if (streamState != VideoStreamState.IDLE) {
                streamState = VideoStreamState.TIMEOUT
            }
        }
    }

    fun setSpsDimensions(width: Int?, height: Int?, resetOutput: Boolean) {
        synchronized(lock) {
            if (width != null && height != null && width > 0 && height > 0) {
                spsWidth = width
                spsHeight = height
            } else {
                spsWidth = null
                spsHeight = null
            }
            if (resetOutput) {
                outputWidth = null
                outputHeight = null
            }
        }
    }

    fun setOutputDimensions(width: Int?, height: Int?) {
        synchronized(lock) {
            if (width != null && height != null && width > 0 && height > 0) {
                outputWidth = width
                outputHeight = height
            } else {
                outputWidth = null
                outputHeight = null
            }
        }
    }

    fun setState(state: VideoStreamState) {
        synchronized(lock) {
            streamState = state
        }
    }

    fun setDecoderError(error: String?) {
        synchronized(lock) {
            decoderError = error
            if (error != null) {
                streamState = VideoStreamState.DECODER_ERROR
            }
        }
    }

    fun recordFrameReceived(bytes: Int, missed: Int, nowMs: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            val safeBytes = bytes.coerceAtLeast(0)
            totalFramesReceived++
            if (missed > 0) {
                missedFrames += missed.toLong()
            }
            lastFrameTimestampMs = nowMs
            if (rateWindowStartMs == 0L) {
                rateWindowStartMs = nowMs
            }
            rxSamples.addLast(RxSample(nowMs, safeBytes))
            rxWindowBytes += safeBytes.toLong()
            pruneExpiredLocked(nowMs)
        }
    }

    fun recordDecodedFrames(count: Int, nowMs: Long = SystemClock.elapsedRealtime()) {
        if (count <= 0) return
        synchronized(lock) {
            decodedFrames += count.toLong()
            if (rateWindowStartMs == 0L) {
                rateWindowStartMs = nowMs
            }
            repeat(count) {
                decTimestamps.addLast(nowMs)
            }
            pruneExpiredLocked(nowMs)
        }
    }

    fun snapshot(nowMs: Long = SystemClock.elapsedRealtime()): VideoMetrics {
        synchronized(lock) {
            pruneExpiredLocked(nowMs)

            val spanMs = if (rateWindowStartMs > 0L && nowMs >= rateWindowStartMs) {
                (nowMs - rateWindowStartMs).coerceAtMost(windowMs)
            } else {
                0L
            }

            val canReportRates = streamState != VideoStreamState.IDLE &&
                streamState != VideoStreamState.TIMEOUT &&
                spanMs >= minRateSpanMs

            val rxFps: Float?
            val kbps: Int?
            if (canReportRates && rxSamples.isNotEmpty()) {
                rxFps = (rxSamples.size * 1000f) / spanMs.toFloat()
                kbps = ((rxWindowBytes * 8.0) / spanMs.toDouble()).roundToInt()
            } else {
                rxFps = null
                kbps = null
            }

            val decFps: Float? = if (canReportRates && (decTimestamps.isNotEmpty() || streamState == VideoStreamState.RECEIVING)) {
                (decTimestamps.size * 1000f) / spanMs.toFloat()
            } else {
                null
            }

            return VideoMetrics(
                state = streamState,
                spsWidth = spsWidth,
                spsHeight = spsHeight,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                receivedFps = rxFps,
                decodedFps = decFps,
                bitrateKbps = kbps,
                totalFramesReceived = totalFramesReceived,
                decodedFrames = decodedFrames,
                missedFrames = missedFrames,
                lastFrameTimestampMs = lastFrameTimestampMs,
                decoderError = decoderError,
            )
        }
    }

    private fun clearRateWindowsLocked() {
        rateWindowStartMs = 0L
        rxSamples.clear()
        rxWindowBytes = 0L
        decTimestamps.clear()
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (rxSamples.isNotEmpty() && rxSamples.first().timestampMs < cutoff) {
            val removed = rxSamples.removeFirst()
            rxWindowBytes = (rxWindowBytes - removed.bytes.toLong()).coerceAtLeast(0L)
        }
        while (decTimestamps.isNotEmpty() && decTimestamps.first() < cutoff) {
            decTimestamps.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
        const val DEFAULT_MIN_RATE_SPAN_MS = 500L
    }
}
