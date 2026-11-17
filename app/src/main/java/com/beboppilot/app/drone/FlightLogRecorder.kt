package com.beboppilot.app.drone

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.beboppilot.app.sdk.Bebop2LinkState
import com.beboppilot.app.sdk.BebopPilotUiState
import com.beboppilot.app.sdk.FlightLogStatus
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Grabador ligero y asíncrono de bitácora de telemetría y eventos de vuelo en CSV.
 * Escribe en almacenamiento específico de la app (sin requerir permisos extra) y permite
 * exportar el último registro a Descargas/BebopPilot/FlightLogs.
 */
class FlightLogRecorder(private val context: Context) {

    companion object {
        private const val TAG = "FlightLogRecorder"
        private const val LOG_DIR_NAME = "flight_logs"
        private const val MAX_KEEP_FILES = 25
        private const val AIRBORNE_SAMPLE_INTERVAL_MS = 250L
        private const val GROUND_SAMPLE_INTERVAL_MS = 1000L
        private const val ATTITUDE_WARN_THRESHOLD_DEG = 14f
        private const val ATTITUDE_WARN_COOLDOWN_MS = 2000L
        private const val MAX_RECENT_EVENTS = 14
        private const val CSV_HEADER =
            "timestamp_iso,elapsed_ms,flying_state,battery_pct,wifi_rssi_dbm,alt_m,speed_ms," +
                "roll_deg,pitch_deg,yaw_deg,gps_fix,gps_sats,lat,lon,max_tilt_deg," +
                "max_yaw_deg_s,max_pr_deg_s,max_vert_ms,banked_turn,hull_prot,magneto_req," +
                "alert,sensors_ko,event_category,event_detail"
    }

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopFlightLogThread").apply { isDaemon = true }
    }
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val shortTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var activeFile: File? = null
    private var latestFile: File? = null
    private var writer: BufferedWriter? = null
    private var sessionStartMs: Long = 0L
    private var lastSampleElapsedMs: Long = -10_000L
    private var lastAttitudeWarnMs: Long = -10_000L
    private var sampleCount: Int = 0
    private var eventCount: Int = 0
    private var linesSinceFlush: Int = 0
    private val recentEvents = ArrayDeque<String>(MAX_RECENT_EVENTS)
    private var lastExportNotice: String? = null

    @Volatile
    private var statusSnapshot = FlightLogStatus()

    var onStatusUpdated: ((FlightLogStatus) -> Unit)? = null

    fun currentStatus(): FlightLogStatus = statusSnapshot

    fun startSession(droneName: String?) {
        ioExecutor.execute {
            closeSessionLocked(reason = null)
            val dir = resolveLogDir() ?: return@execute
            pruneOldLogsLocked(dir)

            val now = Date()
            val file = File(dir, "Bebop2_FlightLog_${fileStampFormat.format(now)}.csv")
            try {
                val bw = BufferedWriter(FileWriter(file, false))
                bw.write(CSV_HEADER)
                bw.newLine()
                bw.flush()

                activeFile = file
                latestFile = file
                writer = bw
                sessionStartMs = SystemClock.elapsedRealtime()
                lastSampleElapsedMs = -10_000L
                lastAttitudeWarnMs = -10_000L
                sampleCount = 0
                eventCount = 0
                linesSinceFlush = 0
                recentEvents.clear()
                lastExportNotice = null
                appendRecentEventLocked("Sesión iniciada (${droneName ?: "Bebop 2"})")
                publishStatusLocked()
            } catch (t: Throwable) {
                Log.w(TAG, "No se pudo abrir archivo de bitácora ${file.name}", t)
            }
        }
    }

    fun endSession(reason: String) {
        ioExecutor.execute {
            closeSessionLocked(reason)
        }
    }

    fun maybeRecordTelemetry(state: BebopPilotUiState) {
        if (state.bebop2State != Bebop2LinkState.CONNECTED) return
        val nowMs = SystemClock.elapsedRealtime()
        val flying = simplifyFlyingState(state.bebop2FlyingState)
        val isAirborne = flying == "HOVERING" || flying == "FLYING" || flying == "TAKINGOFF" || flying == "LANDING"
        val minInterval = if (isAirborne) AIRBORNE_SAMPLE_INTERVAL_MS else GROUND_SAMPLE_INTERVAL_MS
        if (nowMs - lastSampleElapsedMs < minInterval) return
        lastSampleElapsedMs = nowMs

        ioExecutor.execute {
            val bw = writer ?: return@execute
            val rollDeg = state.telemetry.rollRad?.let { it * 180f / PI.toFloat() }
            val pitchDeg = state.telemetry.pitchRad?.let { it * 180f / PI.toFloat() }

            var autoEventCat = ""
            var autoEventDetail = ""
            if (flying == "HOVERING" && rollDeg != null && pitchDeg != null &&
                (abs(rollDeg) >= ATTITUDE_WARN_THRESHOLD_DEG || abs(pitchDeg) >= ATTITUDE_WARN_THRESHOLD_DEG) &&
                nowMs - lastAttitudeWarnMs >= ATTITUDE_WARN_COOLDOWN_MS
            ) {
                lastAttitudeWarnMs = nowMs
                autoEventCat = "ATTITUDE_HOVER"
                autoEventDetail = String.format(
                    Locale.US,
                    "Oscilación en hover: roll=%.1f° pitch=%.1f° alt=%.1fm",
                    rollDeg,
                    pitchDeg,
                    state.telemetry.altitudeMeters ?: 0.0,
                )
                eventCount++
                appendRecentEventLocked(autoEventDetail)
            }

            writeCsvRowLocked(bw, state, autoEventCat, autoEventDetail, flushImmediately = autoEventCat.isNotEmpty())
            sampleCount++
            publishStatusLocked()
        }
    }

    fun recordEvent(state: BebopPilotUiState, category: String, detail: String) {
        ioExecutor.execute {
            val bw = writer ?: return@execute
            eventCount++
            appendRecentEventLocked("$category: $detail")
            writeCsvRowLocked(bw, state, category, detail, flushImmediately = true)
            sampleCount++
            publishStatusLocked()
        }
    }

    fun exportLatestToDownloads(onDone: (Result<String>) -> Unit) {
        ioExecutor.execute {
            try {
                writer?.flush()
                val source = activeFile?.takeIf { it.exists() }
                    ?: latestFile?.takeIf { it.exists() }
                    ?: resolveLogDir()?.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".csv") }
                        ?.maxByOrNull { it.lastModified() }

                if (source == null) {
                    val err = IllegalStateException("Todavía no hay bitácoras guardadas.")
                    lastExportNotice = err.message
                    publishStatusLocked()
                    onDone(Result.failure(err))
                    return@execute
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/BebopPilot/FlightLogs",
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("MediaStore rechazó crear ${source.name}")
                    resolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("No se pudo abrir flujo de salida")

                    val doneValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                    val display = "Descargas/BebopPilot/FlightLogs/${source.name}"
                    lastExportNotice = "Guardado en $display"
                    publishStatusLocked()
                    onDone(Result.success(display))
                } else {
                    val display = source.absolutePath
                    lastExportNotice = "Archivo local: $display"
                    publishStatusLocked()
                    onDone(Result.success(display))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error exportando bitácora", t)
                lastExportNotice = "Error exportando bitácora: ${t.message}"
                publishStatusLocked()
                onDone(Result.failure(t))
            }
        }
    }

    fun release() {
        ioExecutor.execute {
            closeSessionLocked("Aplicación cerrada")
        }
    }

    private fun closeSessionLocked(reason: String?) {
        val bw = writer ?: return
        try {
            if (!reason.isNullOrBlank()) {
                val nowIso = isoFormat.format(Date())
                val elapsed = (SystemClock.elapsedRealtime() - sessionStartMs).coerceAtLeast(0L)
                bw.write("$nowIso,$elapsed,,,,,,,,,,,,,,,,,,,,,,SESSION_END,${csvEscape(reason)}")
                bw.newLine()
                appendRecentEventLocked("Sesión cerrada ($reason)")
            }
            bw.flush()
            bw.close()
        } catch (_: Throwable) {
        } finally {
            writer = null
            activeFile = null
            publishStatusLocked()
        }
    }

    private fun writeCsvRowLocked(
        bw: BufferedWriter,
        state: BebopPilotUiState,
        eventCategory: String,
        eventDetail: String,
        flushImmediately: Boolean,
    ) {
        try {
            val now = Date()
            val elapsed = (SystemClock.elapsedRealtime() - sessionStartMs).coerceAtLeast(0L)
            val t = state.telemetry
            val fs = state.flightSettings
            val flying = simplifyFlyingState(state.bebop2FlyingState)
            val radToDeg = 180f / PI.toFloat()
            val rollDeg = t.rollRad?.let { String.format(Locale.US, "%.2f", it * radToDeg) } ?: ""
            val pitchDeg = t.pitchRad?.let { String.format(Locale.US, "%.2f", it * radToDeg) } ?: ""
            val yawDeg = t.yawRad?.let { String.format(Locale.US, "%.2f", it * radToDeg) } ?: ""
            val speed = if (t.speedX != null && t.speedY != null) {
                String.format(Locale.US, "%.2f", sqrt(t.speedX * t.speedX + t.speedY * t.speedY))
            } else {
                ""
            }
            val sensorsKo = t.sensors.filter { !it.ok }.joinToString("|") { it.name }

            val row = listOf(
                isoFormat.format(now),
                elapsed.toString(),
                flying,
                state.bebop2BatteryPercent?.toString() ?: "",
                t.droneWifiRssiDbm?.toString() ?: "",
                t.altitudeMeters?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                speed,
                rollDeg,
                pitchDeg,
                yawDeg,
                t.gpsFixed?.let { if (it) "1" else "0" } ?: "",
                t.gpsSatellites?.toString() ?: "",
                t.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "",
                t.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "",
                String.format(Locale.US, "%.1f", fs.maxTilt.current),
                String.format(Locale.US, "%.1f", fs.maxRotationSpeed.current),
                String.format(Locale.US, "%.1f", fs.maxPitchRollRotationSpeed.current),
                String.format(Locale.US, "%.2f", fs.maxVerticalSpeed.current),
                if (fs.bankedTurnKnown) (if (fs.bankedTurnEnabled) "1" else "0") else "",
                if (fs.hullProtectionKnown) (if (fs.hullProtectionPresent) "1" else "0") else "",
                if (fs.magnetoCalibration.requiredKnown) (if (fs.magnetoCalibration.required) "1" else "0") else "",
                csvEscape(fs.alertState ?: ""),
                csvEscape(sensorsKo),
                csvEscape(eventCategory),
                csvEscape(eventDetail),
            ).joinToString(",")

            bw.write(row)
            bw.newLine()
            linesSinceFlush++
            if (flushImmediately || linesSinceFlush >= 4) {
                bw.flush()
                linesSinceFlush = 0
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error escribiendo fila de bitácora", t)
        }
    }

    private fun resolveLogDir(): File? {
        val base = context.getExternalFilesDir(LOG_DIR_NAME) ?: File(context.filesDir, LOG_DIR_NAME)
        if (!base.exists() && !base.mkdirs()) {
            Log.w(TAG, "No se pudo crear directorio ${base.absolutePath}")
            return null
        }
        return base
    }

    private fun pruneOldLogsLocked(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size >= MAX_KEEP_FILES) {
            files.drop(MAX_KEEP_FILES - 1).forEach { runCatching { it.delete() } }
        }
    }

    private fun appendRecentEventLocked(msg: String) {
        val line = "${shortTimeFormat.format(Date())} · $msg"
        if (recentEvents.size >= MAX_RECENT_EVENTS) {
            recentEvents.removeLast()
        }
        recentEvents.addFirst(line)
    }

    private fun publishStatusLocked() {
        val snapshot = FlightLogStatus(
            activeFileName = (activeFile ?: latestFile)?.name,
            sampleCount = sampleCount,
            eventCount = eventCount,
            recentEvents = recentEvents.toList(),
            lastExportNotice = lastExportNotice,
        )
        statusSnapshot = snapshot
        onStatusUpdated?.invoke(snapshot)
    }

    private fun simplifyFlyingState(raw: String?): String {
        if (raw == null) return ""
        return raw.removePrefix("ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_")
    }

    private fun csvEscape(raw: String): String {
        if (raw.isEmpty()) return ""
        val needsQuotes = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (!needsQuotes) raw else "\"${raw.replace("\"", "\"\"")}\""
    }
}
