package com.beboppilot.app.video

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.round

/**
 * Estima la luminancia de los fotogramas renderizados mediante PixelCopy en un HandlerThread
 * secundario y propone ajustes graduales de EV con histéresis y enfriamiento anti-oscilación.
 */
class AutoExposureController(
    private val onLuminanceEstimated: (Float) -> Unit,
    private val onRequestExposureChange: (Float) -> Unit,
) {

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var sampleBitmap: Bitmap? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var streamReceiving = false

    @Volatile
    private var surfaceProvider: (() -> SurfaceView?)? = null

    @Volatile
    private var currentEv = 0f

    @Volatile
    private var minEv = -3f

    @Volatile
    private var maxEv = 3f

    private var emaLuminance: Float? = null
    private var lastCommandSentMs = 0L
    private var isLoopScheduled = false
    private var inFlightCopy = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            isLoopScheduled = false
            if (!enabled) return
            sampleFrameAndEvaluate()
            scheduleNextTick()
        }
    }

    fun bindSurfaceProvider(provider: (() -> SurfaceView?)?) {
        surfaceProvider = provider
    }

    fun updateStreamReceiving(receiving: Boolean) {
        streamReceiving = receiving
        if (enabled && receiving) {
            ensureThreadStarted()
            workerHandler?.post { scheduleNextTick(immediate = true) }
        }
    }

    fun updateDroneExposureBounds(current: Float, min: Float, max: Float) {
        currentEv = current
        minEv = min
        maxEv = max
    }

    fun setAutoEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        if (isEnabled) {
            emaLuminance = null
            ensureThreadStarted()
            workerHandler?.post { scheduleNextTick(immediate = true) }
        } else {
            workerHandler?.removeCallbacks(tickRunnable)
            isLoopScheduled = false
        }
    }

    fun release() {
        enabled = false
        streamReceiving = false
        surfaceProvider = null
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        sampleBitmap?.recycle()
        sampleBitmap = null
    }

    private fun ensureThreadStarted() {
        if (workerThread != null) return
        val thread = HandlerThread("BebopPilot-AutoExp").apply { start() }
        workerThread = thread
        workerHandler = Handler(thread.looper)
    }

    private fun scheduleNextTick(immediate: Boolean = false) {
        val handler = workerHandler ?: return
        if (!enabled || isLoopScheduled) return
        isLoopScheduled = true
        handler.postDelayed(tickRunnable, if (immediate) 250L else SAMPLE_INTERVAL_MS)
    }

    private fun sampleFrameAndEvaluate() {
        if (!enabled || !streamReceiving || inFlightCopy) return
        val view = surfaceProvider?.invoke() ?: return
        val surface = view.holder.surface
        if (surface == null || !surface.isValid || view.width <= 0 || view.height <= 0) return
        val handler = workerHandler ?: return

        val bmp = sampleBitmap ?: Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888).also {
            sampleBitmap = it
        }

        inFlightCopy = true
        try {
            PixelCopy.request(
                view,
                bmp,
                { result ->
                    inFlightCopy = false
                    if (result == PixelCopy.SUCCESS && enabled && streamReceiving) {
                        val luma = computeCenterWeightedLuminance(bmp)
                        processLuminanceSample(luma)
                    }
                },
                handler
            )
        } catch (_: Throwable) {
            inFlightCopy = false
        }
    }

    private fun computeCenterWeightedLuminance(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var weightedSum = 0.0
        var totalWeight = 0.0
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = cx + cy

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val px = pixels[rowOffset + x]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val yLuma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                val dist = abs(x - cx) + abs(y - cy)
                val weight = 1.0 + 1.5 * (1.0 - (dist / maxDist).coerceIn(0f, 1f))
                weightedSum += yLuma * weight
                totalWeight += weight
            }
        }
        return if (totalWeight > 0.0) (weightedSum / totalWeight).toFloat().coerceIn(0f, 1f) else 0f
    }

    private fun processLuminanceSample(rawLuma: Float) {
        val prev = emaLuminance
        val smoothed = if (prev == null) rawLuma else (prev * (1f - EMA_ALPHA) + rawLuma * EMA_ALPHA)
        emaLuminance = smoothed
        onLuminanceEstimated(smoothed)

        val nowMs = SystemClock.elapsedRealtime()
        if ((nowMs - lastCommandSentMs) < COMMAND_COOLDOWN_MS) {
            return
        }

        val error = TARGET_LUMINANCE - smoothed
        if (abs(error) <= HYSTERESIS_DEADBAND) {
            return
        }

        // Paso proporcional acotado y cuantizado a 0.25 EV para evitar saltos bruscos y oscilaciones.
        val rawStep = (error * PROPORTIONAL_GAIN).coerceIn(-MAX_EV_STEP, MAX_EV_STEP)
        val snappedStep = (round(rawStep / EV_STEP_GRANULARITY) * EV_STEP_GRANULARITY)
        if (abs(snappedStep) < EV_STEP_GRANULARITY) {
            return
        }

        val candidateEv = (currentEv + snappedStep).coerceIn(minEv, maxEv)
        val roundedEv = (round(candidateEv / EV_STEP_GRANULARITY) * EV_STEP_GRANULARITY).coerceIn(minEv, maxEv)
        if (abs(roundedEv - currentEv) < 0.05f) {
            return
        }

        lastCommandSentMs = nowMs
        currentEv = roundedEv
        onRequestExposureChange(roundedEv)
    }

    companion object {
        private const val SAMPLE_WIDTH = 64
        private const val SAMPLE_HEIGHT = 36
        private const val SAMPLE_INTERVAL_MS = 1_100L
        private const val COMMAND_COOLDOWN_MS = 2_200L
        private const val TARGET_LUMINANCE = 0.45f
        private const val HYSTERESIS_DEADBAND = 0.07f
        private const val EMA_ALPHA = 0.35f
        private const val PROPORTIONAL_GAIN = 1.15f
        private const val MAX_EV_STEP = 0.25f
        private const val EV_STEP_GRANULARITY = 0.25f
    }
}
