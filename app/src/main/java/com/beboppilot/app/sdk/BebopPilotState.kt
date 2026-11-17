package com.beboppilot.app.sdk

import android.os.Build

enum class OverallConnectionPhase(val label: String) {
    USB_DISCONNECTED("USB desconectado"),
    USB_PERMISSION_PENDING("Permiso USB pendiente"),
    USB_CONNECTED("USB conectado"),
    MUX_INITIALIZING("MUX inicializando"),
    MUX_CONNECTED("MUX conectado"),
    SEARCHING_BEBOP2("Buscando Bebop 2"),
    BEBOP2_CONNECTED("Bebop 2 conectado"),
    RECEIVING_VIDEO("Recibiendo video"),
    ERROR("Error de conexión"),
}

enum class UsbConnectionState(val label: String) {
    DISCONNECTED("USB desconectado"),
    PERMISSION_PENDING("Permiso USB pendiente"),
    PERMISSION_DENIED("Permiso USB denegado"),
    CONNECTED("USB conectado"),
}

enum class MuxConnectionState(val label: String) {
    IDLE("MUX inactivo"),
    INITIALIZING("MUX inicializando"),
    CONNECTED("MUX conectado"),
    ERROR("Error MUX"),
}

enum class SkyControllerLinkState(val label: String) {
    DISCONNECTED("Desconectado"),
    DISCOVERED("Detectado por MUX"),
    CONNECTING("Conectando"),
    CONNECTED("Conectado"),
    DISCONNECTING("Desconectando"),
    ERROR("Error"),
}

enum class Bebop2LinkState(val label: String) {
    NOT_DETECTED("No detectado"),
    SEARCHING("Buscando Bebop 2"),
    CONNECTED("Bebop 2 conectado"),
    DISCONNECTED("Bebop 2 desconectado"),
}

enum class VideoStreamState(val label: String) {
    IDLE("Video inactivo"),
    STARTING("Habilitando stream de video"),
    WAITING_DECODER("Esperando SPS/PPS"),
    WAITING_FIRST_FRAME("Esperando primer I-frame H.264"),
    RECEIVING("Recibiendo video"),
    TIMEOUT("Timeout de recepción de video"),
    DECODER_ERROR("Error de decodificador"),
}

enum class WhiteBalanceMode(val label: String) {
    AUTO("Automático"),
    DAYLIGHT("Luz diurna"),
    CLOUDY("Nublado"),
    TUNGSTEN("Tungsteno"),
    COOL_WHITE("Fluorescente"),
    UNKNOWN("Desconocido"),
}

enum class ReturnHomeType(val label: String) {
    TAKEOFF("Despegue"),
    PILOT("Piloto"),
    FOLLOWEE("Objetivo"),
    UNKNOWN("Desconocido"),
}

enum class StickExpoMode(val label: String) {
    LINEAR("Lineal"),
    EXPO_0("Suave (0)"),
    EXPO_1("Media (1)"),
    EXPO_2("Fuerte (2)"),
    EXPO_4("Máxima (4)"),
    UNKNOWN("N/D"),
}

enum class FlightPreset(
    val label: String,
    val description: String,
    val targetTiltDeg: Float,
    val targetYawDegSec: Float,
    val targetPitchRollRateDegSec: Float,
    val targetVerticalMs: Float,
    val bankedTurn: Boolean,
) {
    VIDEO(
        label = "Cine",
        description = "Suave para vídeo",
        targetTiltDeg = 10f,
        targetYawDegSec = 45f,
        targetPitchRollRateDegSec = 120f,
        targetVerticalMs = 1.5f,
        bankedTurn = true,
    ),
    NORMAL(
        label = "Normal",
        description = "Equilibrado",
        targetTiltDeg = 18f,
        targetYawDegSec = 100f,
        targetPitchRollRateDegSec = 200f,
        targetVerticalMs = 3.0f,
        bankedTurn = false,
    ),
    SPORT(
        label = "Sport",
        description = "Giro y empuje máx.",
        targetTiltDeg = 35f,
        targetYawDegSec = 200f,
        targetPitchRollRateDegSec = 300f,
        targetVerticalMs = 5.0f,
        bankedTurn = false,
    ),
}

enum class VideoResolutionProfile(val shortLabel: String, val label: String, val detail: String) {
    REC_1080P_STREAM_480P(
        shortLabel = "1080p / 480p",
        label = "Full HD Dron (1080p)",
        detail = "Dron graba Full HD 1080p · Celular graba/ve 480p",
    ),
    REC_720P_STREAM_720P(
        shortLabel = "720p HD",
        label = "HD Dron + Celular (720p)",
        detail = "Dron y celular graban/ven en 720p HD",
    ),
    UNKNOWN("N/D", "Sin confirmar", ""),
}

enum class VideoFramerateMode(val label: String) {
    FPS_24("24 fps"),
    FPS_25("25 fps"),
    FPS_30("30 fps"),
    UNKNOWN("N/D"),
}

enum class VideoRecordingQualityMode(val label: String, val detail: String) {
    QUALITY("Máx. calidad", "Prioriza detalle en el dron"),
    TIME("Más tiempo", "Reduce tamaño de archivo"),
    UNKNOWN("N/D", ""),
}

enum class VideoStreamReliabilityMode(val label: String) {
    LOW_LATENCY("Baja latencia"),
    HIGH_RELIABILITY("Alta fiabilidad"),
    HIGH_RELIABILITY_LOW_FPS("Fiabilidad (FPS red.)"),
    UNKNOWN("N/D"),
}

enum class VideoStabilizationMode(val label: String) {
    ROLL_PITCH("2 ejes"),
    PITCH("Pitch"),
    ROLL("Roll"),
    NONE("Desactivada"),
    UNKNOWN("N/D"),
}

enum class DroneRecordState(val label: String) {
    UNKNOWN("Sin confirmar"),
    STOPPED("Detenida"),
    STARTING("Iniciando..."),
    RECORDING("Grabando"),
    STOPPING("Deteniendo..."),
    NOT_AVAILABLE("No disponible"),
    ERROR("Error"),
}

enum class PhotoCaptureState(val label: String) {
    IDLE("Lista"),
    CAPTURING("Capturando..."),
    TAKEN("Foto guardada"),
    BUSY("Cámara ocupada"),
    NOT_AVAILABLE("No disponible"),
    ERROR("Error"),
}

enum class LocalRecordState(val label: String) {
    IDLE("Detenida"),
    WAITING_CONFIG("Esperando SPS/PPS"),
    WAITING_KEYFRAME("Esperando I-frame"),
    RECORDING("Grabando MP4"),
    STOPPING("Finalizando MP4"),
    ERROR("Error MP4"),
}

data class BebopFirmwareInfo(
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
) {
    val isKnown: Boolean
        get() = !softwareVersion.isNullOrBlank()

    val supportsVideoResolutionControls: Boolean
        get() = FirmwareVersionComparator.isAtLeast(softwareVersion, 3, 4, 0)
}

object FirmwareVersionComparator {
    data class ParsedVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ParsedVersion> {
        override fun compareTo(other: ParsedVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }

    fun parse(rawVersion: String?): ParsedVersion? {
        val cleaned = rawVersion?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (cleaned.isEmpty()) return null
        val numericPrefix = cleaned.takeWhile { it.isDigit() || it == '.' }
        if (numericPrefix.isEmpty()) return null
        val parts = numericPrefix.split('.').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = if (parts.size >= 3) (parts[2].toIntOrNull() ?: return null) else 0
        return ParsedVersion(major, minor, patch)
    }

    fun isAtLeast(rawVersion: String?, major: Int, minor: Int, patch: Int = 0): Boolean {
        val parsed = parse(rawVersion) ?: return false
        return parsed >= ParsedVersion(major, minor, patch)
    }
}

data class VideoMetrics(
    val state: VideoStreamState = VideoStreamState.IDLE,
    val spsWidth: Int? = null,
    val spsHeight: Int? = null,
    val outputWidth: Int? = null,
    val outputHeight: Int? = null,
    val receivedFps: Float? = null,
    val decodedFps: Float? = null,
    val bitrateKbps: Int? = null,
    val totalFramesReceived: Long = 0L,
    val decodedFrames: Long = 0L,
    val missedFrames: Long = 0L,
    val lastFrameTimestampMs: Long = 0L,
    val decoderError: String? = null,
) {
    val effectiveWidth: Int
        get() = outputWidth ?: spsWidth ?: 0

    val effectiveHeight: Int
        get() = outputHeight ?: spsHeight ?: 0

    val effectiveAspectRatio: Float?
        get() {
            val w = effectiveWidth
            val h = effectiveHeight
            return if (w > 0 && h > 0) {
                w.toFloat() / h.toFloat()
            } else {
                null
            }
        }
}

data class CameraSettingsState(
    val exposureValue: Float = 0f,
    val exposureMin: Float = -3f,
    val exposureMax: Float = 3f,
    val exposureKnown: Boolean = false,
    val autoExposureEnabled: Boolean = false,
    val estimatedLuminance: Float? = null,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.UNKNOWN,
    val whiteBalanceKnown: Boolean = false,
    val videoResolution: VideoResolutionProfile = VideoResolutionProfile.UNKNOWN,
    val videoFramerate: VideoFramerateMode = VideoFramerateMode.UNKNOWN,
    val videoRecordingMode: VideoRecordingQualityMode = VideoRecordingQualityMode.UNKNOWN,
    val videoStreamMode: VideoStreamReliabilityMode = VideoStreamReliabilityMode.UNKNOWN,
    val videoStabilization: VideoStabilizationMode = VideoStabilizationMode.UNKNOWN,
    val videoAutorecordEnabled: Boolean = false,
    val videoAutorecordKnown: Boolean = false,
    val videoAutorecordMassStorageId: Int = 0,
    val saturation: BoundedFloatSetting = BoundedFloatSetting(0f, -100f, 100f, false),
    val localPreviewBrightness: Float? = null, // null = brillo del sistema; solo pantalla local
)

enum class MagnetoAxis(val label: String, val shortHint: String, val instruction: String) {
    X_AXIS(
        label = "Eje X (Roll)",
        shortHint = "Nariz adelante · gira 360° de lado",
        instruction = "Pon la cámara mirando adelante y gira el dron 360° sobre su propio largo (de lado → boca abajo → al otro lado → normal).",
    ),
    Y_AXIS(
        label = "Eje Y (Pitch)",
        shortHint = "Nariz arriba → abajo · voltereta 360°",
        instruction = "Sostén el dron de lado y haz una voltereta lenta de 360° (nariz al cielo → boca abajo → nariz al suelo → horizontal).",
    ),
    Z_AXIS(
        label = "Eje Z (Yaw)",
        shortHint = "Dron plano · giro horizontal 360°",
        instruction = "Mantén el dron horizontal (patas abajo, cámara adelante) y gira 360° en círculo plano.",
    ),
    NONE(
        label = "Listo / Esperando",
        shortHint = "Elige un eje arriba para ver cómo mover el dron",
        instruction = "Al pulsar Calibrar brújula, sigue el movimiento de la animación hasta completar X, Y y Z.",
    ),
}

data class MagnetoCalibrationState(
    val required: Boolean = false,
    val requiredKnown: Boolean = false,
    val started: Boolean = false,
    val currentAxis: MagnetoAxis = MagnetoAxis.NONE,
    val xDone: Boolean = false,
    val yDone: Boolean = false,
    val zDone: Boolean = false,
    val failed: Boolean = false,
    val statusNotice: String? = null,
)

data class BoundedFloatSetting(
    val current: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f,
    val known: Boolean = false,
) {
    fun coerceTarget(target: Float): Float =
        if (known && max >= min) target.coerceIn(min, max) else target
}

data class FlightSettingsState(
    val maxRotationSpeed: BoundedFloatSetting = BoundedFloatSetting(100f, 10f, 200f, false),
    val maxTilt: BoundedFloatSetting = BoundedFloatSetting(15f, 5f, 35f, false),
    val maxPitchRollRotationSpeed: BoundedFloatSetting = BoundedFloatSetting(200f, 80f, 300f, false),
    val maxVerticalSpeed: BoundedFloatSetting = BoundedFloatSetting(2.5f, 0.5f, 6f, false),
    val maxAltitude: BoundedFloatSetting = BoundedFloatSetting(50f, 0.5f, 150f, false),
    val maxDistance: BoundedFloatSetting = BoundedFloatSetting(300f, 10f, 2000f, false),
    val geofenceEnabled: Boolean = false,
    val geofenceKnown: Boolean = false,
    val bankedTurnEnabled: Boolean = false,
    val bankedTurnKnown: Boolean = false,
    val hullProtectionPresent: Boolean = false,
    val hullProtectionKnown: Boolean = false,
    val returnHomeType: ReturnHomeType = ReturnHomeType.UNKNOWN,
    val returnHomeTypeKnown: Boolean = false,
    val returnHomeDelaySec: Int = 0,
    val returnHomeDelayKnown: Boolean = false,
    val alertState: String? = null,
    val navigateHomeState: String? = null,
    val flatTrimNotice: String? = null,
    val magnetoCalibration: MagnetoCalibrationState = MagnetoCalibrationState(),
)

data class ControllerExpoState(
    val activeProductId: Short = 0,
    val yawAxisId: Int? = null,
    val pitchAxisId: Int? = null,
    val rollAxisId: Int? = null,
    val gazAxisId: Int? = null,
    val axisExpos: Map<Int, StickExpoMode> = emptyMap(),
) {
    val yawExpo: StickExpoMode
        get() = yawAxisId?.let { axisExpos[it] } ?: StickExpoMode.UNKNOWN
}

data class DroneRecordingState(
    val state: DroneRecordState = DroneRecordState.UNKNOWN,
    val isRequestPending: Boolean = false,
    val startedAtElapsedMs: Long = 0L,
    val durationSec: Long = 0L,
    val errorText: String? = null,
    val isMemoryFull: Boolean = false,
    val isLowBattery: Boolean = false,
    val massStorageTotalMb: Long? = null,
    val massStorageUsedMb: Long? = null,
    val massStorageFreeMb: Long? = null,
    val remainingRecTimeSec: Long? = null,
    val lastSavedNotice: String? = null,
)

data class PhotoStatusState(
    val state: PhotoCaptureState = PhotoCaptureState.IDLE,
    val errorText: String? = null,
    val lastCaptureTimestampMs: Long = 0L,
)

data class LocalRecordingState(
    val state: LocalRecordState = LocalRecordState.IDLE,
    val durationSec: Long = 0L,
    val writtenFrames: Long = 0L,
    val lastSavedDisplayPath: String? = null,
    val errorText: String? = null,
)

data class SensorItemState(
    val name: String,
    val ok: Boolean,
)

data class DroneTelemetryState(
    val altitudeMeters: Double? = null,
    val speedX: Float? = null,
    val speedY: Float? = null,
    val speedZ: Float? = null,
    val rollRad: Float? = null,
    val pitchRad: Float? = null,
    val yawRad: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAltitude: Double? = null,
    val gpsFixed: Boolean? = null,
    val gpsSatellites: Int? = null,
    val droneWifiRssiDbm: Int? = null,
    val skyControllerSignalLevel: Int? = null,
    val overheatRegulation: String? = null,
    val sensors: List<SensorItemState> = emptyList(),
)

data class DroneMediaItem(
    val uuid: String,
    val name: String,
    val date: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val remotePath: String?,
)

data class LocalMediaItem(
    val id: Long,
    val uriString: String,
    val name: String,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val durationMs: Long?,
    val isVideo: Boolean,
    val relativePath: String,
)

data class FlightLogStatus(
    val activeFileName: String? = null,
    val sampleCount: Int = 0,
    val eventCount: Int = 0,
    val recentEvents: List<String> = emptyList(),
    val lastExportNotice: String? = null,
)

data class MediaGalleryState(
    val isSupported: Boolean = true,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val activeDownloadName: String? = null,
    val downloadProgressPercent: Int = 0,
    val isDeleting: Boolean = false,
    val activeDeleteName: String? = null,
    val items: List<DroneMediaItem> = emptyList(),
    val downloadedNames: Set<String> = emptySet(),
    val localItems: List<LocalMediaItem> = emptyList(),
    val localLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

data class BebopPilotUiState(
    val nativeLoadStatus: NativeLoadStatus = NativeLoadStatus.Pending,
    val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val usbState: UsbConnectionState = UsbConnectionState.DISCONNECTED,
    val usbAccessory: UsbAccessorySummary? = null,
    val muxState: MuxConnectionState = MuxConnectionState.IDLE,
    val muxDetail: String? = null,
    val skyControllerState: SkyControllerLinkState = SkyControllerLinkState.DISCONNECTED,
    val skyControllerName: String? = null,
    val skyControllerBatteryPercent: Int? = null,
    val bebop2State: Bebop2LinkState = Bebop2LinkState.NOT_DETECTED,
    val bebop2Name: String? = null,
    val bebop2Product: String? = null,
    val bebop2Firmware: BebopFirmwareInfo = BebopFirmwareInfo(),
    val bebop2BatteryPercent: Int? = null,
    val bebop2FlyingState: String? = null,
    val videoMetrics: VideoMetrics = VideoMetrics(),
    val cameraSettings: CameraSettingsState = CameraSettingsState(),
    val flightSettings: FlightSettingsState = FlightSettingsState(),
    val controllerExpo: ControllerExpoState = ControllerExpoState(),
    val droneRecording: DroneRecordingState = DroneRecordingState(),
    val photoStatus: PhotoStatusState = PhotoStatusState(),
    val localRecording: LocalRecordingState = LocalRecordingState(),
    val telemetry: DroneTelemetryState = DroneTelemetryState(),
    val flightLog: FlightLogStatus = FlightLogStatus(),
    val gallery: MediaGalleryState = MediaGalleryState(),
    val errorMessage: String? = null,
) {
    val overallPhase: OverallConnectionPhase
        get() = when {
            nativeLoadStatus is NativeLoadStatus.Failed -> OverallConnectionPhase.ERROR
            usbState == UsbConnectionState.DISCONNECTED -> OverallConnectionPhase.USB_DISCONNECTED
            usbState == UsbConnectionState.PERMISSION_PENDING -> OverallConnectionPhase.USB_PERMISSION_PENDING
            usbState == UsbConnectionState.PERMISSION_DENIED -> OverallConnectionPhase.ERROR
            muxState == MuxConnectionState.ERROR -> OverallConnectionPhase.ERROR
            muxState == MuxConnectionState.INITIALIZING -> OverallConnectionPhase.MUX_INITIALIZING
            muxState == MuxConnectionState.IDLE -> OverallConnectionPhase.USB_CONNECTED
            videoMetrics.state == VideoStreamState.RECEIVING -> OverallConnectionPhase.RECEIVING_VIDEO
            bebop2State == Bebop2LinkState.CONNECTED -> OverallConnectionPhase.BEBOP2_CONNECTED
            skyControllerState == SkyControllerLinkState.CONNECTED ||
                bebop2State == Bebop2LinkState.SEARCHING -> OverallConnectionPhase.SEARCHING_BEBOP2
            muxState == MuxConnectionState.CONNECTED -> OverallConnectionPhase.MUX_CONNECTED
            else -> OverallConnectionPhase.USB_CONNECTED
        }
}
