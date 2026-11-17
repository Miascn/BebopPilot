package com.beboppilot.app.drone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.beboppilot.app.sdk.Bebop2LinkState
import com.beboppilot.app.sdk.BebopFirmwareInfo
import com.beboppilot.app.sdk.BebopPilotUiState
import com.beboppilot.app.sdk.BoundedFloatSetting
import com.beboppilot.app.sdk.CameraSettingsState
import com.beboppilot.app.sdk.ControllerExpoState
import com.beboppilot.app.sdk.DroneRecordState
import com.beboppilot.app.sdk.DroneRecordingState
import com.beboppilot.app.sdk.DroneTelemetryState
import com.beboppilot.app.sdk.FlightPreset
import com.beboppilot.app.sdk.FlightSettingsState
import com.beboppilot.app.sdk.LocalRecordState
import com.beboppilot.app.sdk.MagnetoAxis
import com.beboppilot.app.sdk.MagnetoCalibrationState
import com.beboppilot.app.sdk.MuxConnectionState
import com.beboppilot.app.sdk.NativeLoadStatus
import com.beboppilot.app.sdk.PhotoCaptureState
import com.beboppilot.app.sdk.PhotoStatusState
import com.beboppilot.app.sdk.ReturnHomeType
import com.beboppilot.app.sdk.SdkInitializer
import com.beboppilot.app.sdk.SensorItemState
import com.beboppilot.app.sdk.SkyControllerLinkState
import com.beboppilot.app.sdk.StickExpoMode
import com.beboppilot.app.sdk.UsbAccessorySummary
import com.beboppilot.app.sdk.UsbConnectionState
import com.beboppilot.app.sdk.VideoFramerateMode
import com.beboppilot.app.sdk.VideoMetrics
import com.beboppilot.app.sdk.VideoRecordingQualityMode
import com.beboppilot.app.sdk.VideoResolutionProfile
import com.beboppilot.app.sdk.VideoStabilizationMode
import com.beboppilot.app.sdk.VideoStreamReliabilityMode
import com.beboppilot.app.sdk.VideoStreamState
import com.beboppilot.app.sdk.WhiteBalanceMode
import com.beboppilot.app.video.AutoExposureController
import com.beboppilot.app.video.BebopH264SurfaceView
import com.beboppilot.app.video.LocalMp4Recorder
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORECORDINGMODE_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORESOLUTIONS_TYPE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM
import com.parrot.arsdk.arcommands.ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM
import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM
import com.parrot.arsdk.arcontroller.ARControllerCodec
import com.parrot.arsdk.arcontroller.ARControllerDictionary
import com.parrot.arsdk.arcontroller.ARDeviceController
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3
import com.parrot.arsdk.arcontroller.ARFeatureCommon
import com.parrot.arsdk.arcontroller.ARFeatureMapper
import com.parrot.arsdk.arcontroller.ARFeatureSkyController
import com.parrot.arsdk.arcontroller.ARFrame
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_NETWORK_TYPE_ENUM
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService
import com.parrot.arsdk.ardiscovery.ARDiscoveryService
import com.parrot.arsdk.ardiscovery.UsbAccessoryMux
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiver
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiverDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.round

private enum class ControllerLifecycleState {
    NEW,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    DISPOSED,
}

private class ManagedControllerSession(
    val id: Long,
    val service: ARDiscoveryDeviceService,
    val controller: ARDeviceController,
) {
    val lifecycle = AtomicReference(ControllerLifecycleState.NEW)
    val stopRequested = AtomicBoolean(false)
    val disposeExecuted = AtomicBoolean(false)
    val everStartedSuccessfully = AtomicBoolean(false)
    val stopSignal = CountDownLatch(1)

    @Volatile
    var videoStreamStarted: Boolean = false

    @Volatile
    var controllerListener: ARDeviceControllerListener? = null

    @Volatile
    var streamListener: ARDeviceControllerStreamListener? = null

    fun canSafelyDispose(): Boolean {
        if (lifecycle.get() == ControllerLifecycleState.STOPPED) return true
        // Solo si start() nunca llegó a crear el hilo nativo StartRun
        return !everStartedSuccessfully.get()
    }
}

/**
 * Retiene referencias fuertes de sesiones ARDeviceController cuya parada nativa sigue en curso,
 * evitando tanto el use-after-free desde disconnectControllerLocked como la recolección por GC
 * (que invocaría ARDeviceController.finalize() -> dispose()). Sobrevive a MainActivity.onDestroy().
 */
private object RetiringControllerTracker {
    private const val TRACKER_TAG = "SkyControllerConnMgr"
    private val pendingSessions = ConcurrentHashMap<Long, ManagedControllerSession>()
    private val disposeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopPilot-ARSDK-Retire")
    }

    fun retain(session: ManagedControllerSession) {
        pendingSessions.putIfAbsent(session.id, session)
    }

    fun pendingCount(): Int = pendingSessions.size

    fun scheduleDispose(session: ManagedControllerSession, reason: String) {
        retain(session)
        disposeExecutor.execute {
            disposeNowIfSafe(session, reason)
        }
    }

    fun disposeNowIfSafe(session: ManagedControllerSession, reason: String) {
        if (!session.canSafelyDispose()) {
            Log.w(
                TRACKER_TAG,
                "[session=#${session.id}] Liberación aplazada ($reason): estado=${session.lifecycle.get()}, everStarted=${session.everStartedSuccessfully.get()}",
            )
            return
        }
        if (!session.disposeExecuted.compareAndSet(false, true)) {
            Log.d(TRACKER_TAG, "[session=#${session.id}] Intento duplicado de dispose() ignorado ($reason)")
            pendingSessions.remove(session.id)
            return
        }

        Log.i(TRACKER_TAG, "[session=#${session.id}] Inicio de dispose() ($reason)")
        try {
            session.streamListener?.let { sl ->
                runCatching { session.controller.removeStreamListener(sl) }
            }
            session.controllerListener?.let { cl ->
                runCatching { session.controller.removeListener(cl) }
            }
            session.controller.dispose()
            session.lifecycle.set(ControllerLifecycleState.DISPOSED)
            Log.i(TRACKER_TAG, "[session=#${session.id}] Finalización de dispose() correcta")
        } catch (t: Throwable) {
            Log.w(TRACKER_TAG, "[session=#${session.id}] Excepción durante dispose()", t)
        } finally {
            pendingSessions.remove(session.id)
        }
    }
}

/**
 * Orquesta el ciclo completo:
 * Teléfono (USB AOA + Mux) <-> Skycontroller 2 <-> Bebop 2 (extensión + cámara + telemetría + H.264).
 *
 * Seguridad: NO envía comandos de vuelo, despegue, aterrizaje ni movimiento del dron.
 */
class SkyControllerConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sdkExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopPilot-ARSDK")
    }

    private val _uiState = MutableStateFlow(BebopPilotUiState())
    val uiState: StateFlow<BebopPilotUiState> = _uiState.asStateFlow()

    @Volatile
    private var videoView: BebopH264SurfaceView? = null

    private var discoveryService: ARDiscoveryService? = null
    private var serviceBound = false
    private var discoveryReceiverRegistered = false
    private val started = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val usbRecoveryInFlight = AtomicBoolean(false)
    private val recoveryToken = AtomicInteger(0)

    @Volatile
    private var isAppInForeground = true

    @Volatile
    private var hasPausedWhileStarted = false

    @Volatile
    private var lastResumedElapsedMs = 0L

    @Volatile
    private var activeSession: ManagedControllerSession? = null

    private val localMp4Recorder = LocalMp4Recorder(appContext) { localState ->
        _uiState.update { it.copy(localRecording = localState) }
        if (localState.state == LocalRecordState.IDLE && localState.lastSavedDisplayPath != null) {
            droneMediaRepository.refreshLocalMedia()
        }
    }

    private val autoExposureController = AutoExposureController(
        onLuminanceEstimated = { luma ->
            _uiState.update { state ->
                state.copy(cameraSettings = state.cameraSettings.copy(estimatedLuminance = luma))
            }
        },
        onRequestExposureChange = { targetEv ->
            sendExposureInternal(targetEv)
        },
    )

    private val droneMediaRepository = DroneMediaRepository(appContext) { galleryState ->
        _uiState.update { it.copy(gallery = galleryState) }
    }

    private val flightLogRecorder = FlightLogRecorder(appContext).apply {
        onStatusUpdated = { logStatus ->
            _uiState.update { it.copy(flightLog = logStatus) }
        }
    }

    private val recordDurationTickRunnable = object : Runnable {
        override fun run() {
            val rec = _uiState.value.droneRecording
            if (rec.state == DroneRecordState.RECORDING && rec.startedAtElapsedMs > 0L) {
                val sec = ((SystemClock.elapsedRealtime() - rec.startedAtElapsedMs) / 1000L).coerceAtLeast(0L)
                _uiState.update {
                    it.copy(droneRecording = it.droneRecording.copy(durationSec = sec))
                }
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    private val recordRequestTimeoutRunnable = Runnable {
        _uiState.update { current ->
            if (!current.droneRecording.isRequestPending) {
                current
            } else {
                val fallback = if (current.droneRecording.startedAtElapsedMs > 0L) {
                    DroneRecordState.RECORDING
                } else {
                    DroneRecordState.STOPPED
                }
                current.copy(
                    droneRecording = current.droneRecording.copy(
                        isRequestPending = false,
                        state = fallback,
                        errorText = "Sin confirmación de grabación del Bebop 2",
                    )
                )
            }
        }
    }

    private val videoMetricsListener = object : BebopH264SurfaceView.MetricsListener {
        override fun onVideoMetricsUpdated(metrics: VideoMetrics) {
            _uiState.update { it.copy(videoMetrics = metrics) }
            autoExposureController.updateStreamReceiving(metrics.state == VideoStreamState.RECEIVING)
            val w = metrics.effectiveWidth
            val h = metrics.effectiveHeight
            if (w > 0 && h > 0) {
                localMp4Recorder.updateVideoDimensions(w, h)
            }
        }
    }

    private val muxStateListener = UsbAccessoryMux.MuxStateListener { state, accessory, detail ->
        Log.i(TAG, "MuxStateChanged: state=$state accessory=$accessory detail=$detail")
        val usbSummary = accessory?.toSummary()
        when (state) {
            UsbAccessoryMux.MuxState.DISCONNECTED -> {
                stopRecordDurationTicker()
                autoExposureController.updateStreamReceiving(false)
                localMp4Recorder.stopRecording()
                droneMediaRepository.attachDeviceService(null)
                _uiState.update {
                    it.copy(
                        usbState = if (accessory != null) UsbConnectionState.CONNECTED else UsbConnectionState.DISCONNECTED,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.IDLE,
                        muxDetail = detail,
                        skyControllerState = SkyControllerLinkState.DISCONNECTED,
                        skyControllerName = null,
                        skyControllerBatteryPercent = null,
                        bebop2State = Bebop2LinkState.NOT_DETECTED,
                        bebop2Name = null,
                        bebop2Product = null,
                        bebop2Firmware = BebopFirmwareInfo(),
                        bebop2BatteryPercent = null,
                        bebop2FlyingState = null,
                        cameraSettings = CameraSettingsState(localPreviewBrightness = it.cameraSettings.localPreviewBrightness),
                        flightSettings = FlightSettingsState(),
                        controllerExpo = ControllerExpoState(),
                        droneRecording = DroneRecordingState(),
                        photoStatus = PhotoStatusState(),
                        telemetry = DroneTelemetryState(),
                        errorMessage = null,
                    )
                }
                sdkExecutor.execute { disconnectControllerLocked("MUX desconectado") }
            }

            UsbAccessoryMux.MuxState.PERMISSION_PENDING -> {
                _uiState.update {
                    it.copy(
                        usbState = UsbConnectionState.PERMISSION_PENDING,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.IDLE,
                        muxDetail = detail,
                        errorMessage = null,
                    )
                }
            }

            UsbAccessoryMux.MuxState.PERMISSION_DENIED -> {
                _uiState.update {
                    it.copy(
                        usbState = UsbConnectionState.PERMISSION_DENIED,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.IDLE,
                        muxDetail = detail,
                        errorMessage = detail ?: "Permiso USB denegado por el usuario",
                    )
                }
            }

            UsbAccessoryMux.MuxState.MUX_INITIALIZING -> {
                _uiState.update {
                    it.copy(
                        usbState = UsbConnectionState.CONNECTED,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.INITIALIZING,
                        muxDetail = detail,
                        errorMessage = null,
                    )
                }
            }

            UsbAccessoryMux.MuxState.MUX_CONNECTED -> {
                _uiState.update {
                    it.copy(
                        usbState = UsbConnectionState.CONNECTED,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.CONNECTED,
                        muxDetail = detail,
                        errorMessage = null,
                    )
                }
            }

            UsbAccessoryMux.MuxState.MUX_ERROR -> {
                stopRecordDurationTicker()
                autoExposureController.updateStreamReceiving(false)
                droneMediaRepository.attachDeviceService(null)
                _uiState.update {
                    it.copy(
                        usbState = if (accessory != null) UsbConnectionState.CONNECTED else UsbConnectionState.DISCONNECTED,
                        usbAccessory = usbSummary,
                        muxState = MuxConnectionState.ERROR,
                        muxDetail = detail,
                        errorMessage = detail ?: "Error al iniciar canal MUX",
                    )
                }
                sdkExecutor.execute { disconnectControllerLocked("Error MUX: $detail") }
            }
        }
    }

    private val discoveryDelegate = ARDiscoveryServicesDevicesListUpdatedReceiverDelegate {
        if (released.get()) return@ARDiscoveryServicesDevicesListUpdatedReceiverDelegate
        if (discoveryService == null) return@ARDiscoveryServicesDevicesListUpdatedReceiverDelegate

        val skyControllerUsbService = findUsbSkyControllerService()
        if (skyControllerUsbService != null) {
            _uiState.update { current ->
                if (current.skyControllerState == SkyControllerLinkState.DISCONNECTED) {
                    current.copy(
                        skyControllerState = SkyControllerLinkState.DISCOVERED,
                        skyControllerName = skyControllerUsbService.name,
                    )
                } else {
                    current
                }
            }
            sdkExecutor.execute {
                connectToSkyControllerLocked(skyControllerUsbService)
            }
        } else {
            sdkExecutor.execute {
                disconnectControllerLocked("Skycontroller 2 removido de ARDiscovery USB")
            }
        }
    }

    private fun findUsbSkyControllerService(): ARDiscoveryDeviceService? {
        val service = discoveryService ?: return null
        val allServices = service.deviceServicesArray ?: emptyList()
        Log.d(TAG, "findUsbSkyControllerService: count=${allServices.size}")
        return allServices.firstOrNull { candidate ->
            val isUsbMux = candidate.networkType == ARDISCOVERY_NETWORK_TYPE_ENUM.ARDISCOVERY_NETWORK_TYPE_USBMUX
            val product = ARDiscoveryService.getProductFromProductID(candidate.productID)
            val isSkyController =
                product == ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_SKYCONTROLLER_2 ||
                    product == ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_SKYCONTROLLER_2P ||
                    product == ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_SKYCONTROLLER_NG
            Log.d(TAG, "Servicio descubierto: name=${candidate.name} net=${candidate.networkType} product=$product")
            isUsbMux && isSkyController
        }
    }

    private var discoveryReceiver: ARDiscoveryServicesDevicesListUpdatedReceiver? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? ARDiscoveryService.LocalBinder ?: return
            val service = localBinder.service
            discoveryService = service
            Log.i(TAG, "ARDiscoveryService enlazado; iniciando SOLO descubrimiento USB (sin Wi-Fi ni BLE)")
            service.startUsbDiscovering()
            mainHandler.post { discoveryDelegate.onServicesDevicesListUpdated() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "ARDiscoveryService desconectado inesperadamente")
            discoveryService = null
        }
    }

    private fun createControllerListener(session: ManagedControllerSession): ARDeviceControllerListener {
        return object : ARDeviceControllerListener {
            override fun onStateChanged(
                controller: ARDeviceController?,
                newState: ARCONTROLLER_DEVICE_STATE_ENUM?,
                error: ARCONTROLLER_ERROR_ENUM?,
            ) {
                Log.i(TAG, "[session=#${session.id}] onStateChanged: state=$newState error=$error")

                if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED) {
                    session.lifecycle.set(ControllerLifecycleState.STOPPED)
                    session.stopSignal.countDown()
                    Log.i(
                        TAG,
                        "[session=#${session.id}] Confirmado ARCONTROLLER_DEVICE_STATE_STOPPED (pendientes=${RetiringControllerTracker.pendingCount()})",
                    )

                    val isCurrent = activeSession === session
                    if (isCurrent) {
                        activeSession = null
                        droneMediaRepository.attachDeviceService(null)
                        stopRecordDurationTicker()
                        autoExposureController.updateStreamReceiving(false)
                        videoView?.onStreamStopped()
                        _uiState.update { current ->
                            val hasError = error != null && error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
                            current.copy(
                                skyControllerState = if (hasError) SkyControllerLinkState.ERROR else SkyControllerLinkState.DISCONNECTED,
                                bebop2State = Bebop2LinkState.NOT_DETECTED,
                                skyControllerBatteryPercent = null,
                                bebop2BatteryPercent = null,
                                bebop2FlyingState = null,
                                cameraSettings = CameraSettingsState(localPreviewBrightness = current.cameraSettings.localPreviewBrightness),
                                flightSettings = FlightSettingsState(),
                                droneRecording = DroneRecordingState(),
                                photoStatus = PhotoStatusState(),
                                telemetry = DroneTelemetryState(),
                                errorMessage = if (hasError) "Error ARDeviceController: $error" else current.errorMessage,
                            )
                        }
                    }

                    // Diferir dispose() fuera del callback para evitar ConcurrentModificationException sobre ArrayList de listeners
                    RetiringControllerTracker.scheduleDispose(
                        session = session,
                        reason = if (isCurrent) "STOPPED en sesión activa" else "STOPPED diferido tras retención",
                    )
                    return
                }

                // Ignorar callbacks tardíos de sesiones retiradas / en parada / tras release()
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    Log.d(TAG, "[session=#${session.id}] Callback onStateChanged($newState) ignorado (sesión no activa)")
                    if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPING) {
                        session.lifecycle.set(ControllerLifecycleState.STOPPING)
                    }
                    return
                }

                val mapped = when (newState) {
                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STARTING -> {
                        session.lifecycle.set(ControllerLifecycleState.STARTING)
                        SkyControllerLinkState.CONNECTING
                    }
                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING -> {
                        session.lifecycle.set(ControllerLifecycleState.RUNNING)
                        SkyControllerLinkState.CONNECTED
                    }
                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPING -> {
                        session.lifecycle.set(ControllerLifecycleState.STOPPING)
                        SkyControllerLinkState.DISCONNECTING
                    }
                    else -> SkyControllerLinkState.ERROR
                }

                val errorText = if (error != null && error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                    "Error ARDeviceController: $error"
                } else {
                    null
                }

                _uiState.update { current ->
                    val nextBebopState = when (mapped) {
                        SkyControllerLinkState.CONNECTED -> {
                            if (current.bebop2State == Bebop2LinkState.CONNECTED) Bebop2LinkState.CONNECTED
                            else Bebop2LinkState.SEARCHING
                        }
                        SkyControllerLinkState.ERROR -> Bebop2LinkState.NOT_DETECTED
                        else -> current.bebop2State
                    }
                    current.copy(
                        skyControllerState = mapped,
                        bebop2State = nextBebopState,
                        bebop2BatteryPercent = if (nextBebopState == Bebop2LinkState.NOT_DETECTED) null else current.bebop2BatteryPercent,
                        bebop2FlyingState = if (nextBebopState == Bebop2LinkState.NOT_DETECTED) null else current.bebop2FlyingState,
                        droneRecording = if (nextBebopState == Bebop2LinkState.NOT_DETECTED) DroneRecordingState() else current.droneRecording,
                        telemetry = if (nextBebopState == Bebop2LinkState.NOT_DETECTED) DroneTelemetryState() else current.telemetry,
                        errorMessage = errorText ?: current.errorMessage,
                    )
                }

                if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED ||
                    mapped == SkyControllerLinkState.ERROR
                ) {
                    localMp4Recorder.stopRecording()
                }

                if (newState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING && controller != null) {
                    val extState = controller.extensionState
                    if (extState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING) {
                        onExtensionStateChanged(
                            controller,
                            extState,
                            controller.extensionProduct,
                            controller.extensionName,
                            ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK,
                        )
                    }
                }
            }

            override fun onExtensionStateChanged(
                controller: ARDeviceController?,
                newState: ARCONTROLLER_DEVICE_STATE_ENUM?,
                product: ARDISCOVERY_PRODUCT_ENUM?,
                name: String?,
                error: ARCONTROLLER_ERROR_ENUM?,
            ) {
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    Log.d(TAG, "[session=#${session.id}] Callback tardío onExtensionStateChanged($newState) ignorado")
                    return
                }
                Log.i(TAG, "[session=#${session.id}] Extension (Dron) onExtensionStateChanged: state=$newState product=$product name=$name error=$error")
                val extError = if (error != null && error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                    "Error de extensión Bebop 2: $error"
                } else {
                    null
                }

                when (newState) {
                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING -> {
                        _uiState.update {
                            it.copy(
                                bebop2State = Bebop2LinkState.CONNECTED,
                                bebop2Name = name ?: product?.name,
                                bebop2Product = product?.name,
                                errorMessage = extError,
                            )
                        }
                        flightLogRecorder.startSession(name ?: product?.name)
                        sdkExecutor.execute { startVideoStreamIfNeededLocked(session) }
                    }

                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STARTING -> {
                        _uiState.update {
                            it.copy(
                                bebop2State = Bebop2LinkState.SEARCHING,
                                bebop2Name = name,
                                bebop2Product = product?.name,
                                errorMessage = extError ?: it.errorMessage,
                            )
                        }
                    }

                    ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED -> {
                        session.videoStreamStarted = false
                        stopRecordDurationTicker()
                        autoExposureController.updateStreamReceiving(false)
                        localMp4Recorder.stopRecording()
                        videoView?.onStreamStopped()
                        flightLogRecorder.endSession("Extensión detenida")
                        _uiState.update { current ->
                            val fallbackState = if (current.skyControllerState == SkyControllerLinkState.CONNECTED) {
                                Bebop2LinkState.SEARCHING
                            } else {
                                Bebop2LinkState.DISCONNECTED
                            }
                            current.copy(
                                bebop2State = fallbackState,
                                bebop2Firmware = BebopFirmwareInfo(),
                                bebop2BatteryPercent = null,
                                bebop2FlyingState = null,
                                cameraSettings = CameraSettingsState(localPreviewBrightness = current.cameraSettings.localPreviewBrightness),
                                flightSettings = FlightSettingsState(),
                                droneRecording = DroneRecordingState(),
                                photoStatus = PhotoStatusState(),
                                telemetry = DroneTelemetryState(),
                                errorMessage = extError ?: current.errorMessage,
                            )
                        }
                    }

                    else -> {
                        if (extError != null) {
                            _uiState.update { it.copy(errorMessage = extError) }
                        }
                    }
                }
            }

            override fun onCommandReceived(
                controller: ARDeviceController?,
                commandKey: ARCONTROLLER_DICTIONARY_KEY_ENUM?,
                elementDictionary: ARControllerDictionary?,
            ) {
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    return
                }
                if (commandKey == null || elementDictionary == null) return
                handleCommandReceived(commandKey, elementDictionary)
            }
        }
    }

    private fun createStreamListener(session: ManagedControllerSession): ARDeviceControllerStreamListener {
        return object : ARDeviceControllerStreamListener {
            override fun configureDecoder(
                controller: ARDeviceController?,
                codec: ARControllerCodec?,
            ): ARCONTROLLER_ERROR_ENUM {
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
                }
                if (codec != null) {
                    videoView?.configureDecoder(codec)
                    if (codec.type == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
                        val h264 = codec.asH264
                        val sps = h264.sps?.byteData
                        val pps = h264.pps?.byteData
                        if (sps != null && sps.isNotEmpty() && pps != null && pps.isNotEmpty()) {
                            localMp4Recorder.onCodecConfigured(sps, pps)
                        }
                    }
                }
                return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
            }

            override fun onFrameReceived(
                controller: ARDeviceController?,
                frame: ARFrame?,
            ): ARCONTROLLER_ERROR_ENUM {
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
                }
                if (frame != null) {
                    videoView?.displayFrame(frame)
                    val size = frame.dataSize
                    val bytes = frame.byteData
                    if (bytes != null && size > 0) {
                        localMp4Recorder.onFrameReceived(bytes, size, frame.isIFrame, frame.missed)
                    }
                }
                return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
            }

            override fun onFrameTimeout(controller: ARDeviceController?) {
                if (released.get() || activeSession !== session || session.stopRequested.get()) {
                    return
                }
                Log.w(TAG, "[session=#${session.id}] onFrameTimeout recibido desde ARDeviceController")
                videoView?.onFrameTimeout()
            }
        }
    }

    fun attachVideoView(view: BebopH264SurfaceView?) {
        videoView?.metricsListener = null
        videoView = view
        view?.metricsListener = videoMetricsListener
        autoExposureController.bindSurfaceProvider(if (view != null) ({ view }) else null)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            refreshUsbAndMux()
            return
        }

        val nativeStatus = SdkInitializer.loadNativeLibraries()
        _uiState.update { it.copy(nativeLoadStatus = nativeStatus) }
        droneMediaRepository.refreshLocalMedia()
        if (nativeStatus !is NativeLoadStatus.Loaded) {
            Log.e(TAG, "No se pudo cargar ARSDK nativo: $nativeStatus")
            _uiState.update {
                it.copy(errorMessage = (nativeStatus as? NativeLoadStatus.Failed)?.error)
            }
            started.set(false)
            return
        }

        Log.i(TAG, "Bibliotecas nativas ARSDK cargadas; iniciando capa USB/MUX y ARDiscoveryService")
        UsbAccessoryMux.get(appContext).setMuxStateListener(muxStateListener)

        ensureDiscoveryInfrastructureStarted()
    }

    private fun ensureDiscoveryInfrastructureStarted() {
        if (released.get() || _uiState.value.nativeLoadStatus !is NativeLoadStatus.Loaded) return

        if (!discoveryReceiverRegistered) {
            val receiver = discoveryReceiver
                ?: ARDiscoveryServicesDevicesListUpdatedReceiver(discoveryDelegate).also {
                    discoveryReceiver = it
                }
            LocalBroadcastManager.getInstance(appContext).registerReceiver(
                receiver,
                IntentFilter(ARDiscoveryService.kARDiscoveryServiceNotificationServicesDevicesListUpdated),
            )
            discoveryReceiverRegistered = true
        }

        val currentService = discoveryService
        if (!serviceBound || currentService == null) {
            val intent = Intent(appContext, ARDiscoveryService::class.java)
            serviceBound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "bindService(ARDiscoveryService) -> $serviceBound")
        } else {
            runCatching { currentService.startUsbDiscovering() }
        }
    }

    fun onAppPaused() {
        isAppInForeground = false
        if (started.get() && !released.get()) {
            hasPausedWhileStarted = true
        }
        cancelRecoveryChecks()
    }

    fun onAppResumed() {
        isAppInForeground = true
        if (released.get() || !started.get()) return
        if (_uiState.value.nativeLoadStatus !is NativeLoadStatus.Loaded) return
        if (!hasPausedWhileStarted) return

        hasPausedWhileStarted = false
        val resumeMs = SystemClock.elapsedRealtime()
        lastResumedElapsedMs = resumeMs
        val token = recoveryToken.incrementAndGet()
        cancelRecoveryChecks()

        Log.i(TAG, "Regreso a primer plano (onResume); verificando USB/MUX, sesión y vídeo")
        ensureDiscoveryInfrastructureStarted()

        val mux = UsbAccessoryMux.get(appContext)
        if (!mux.hasSupportedAccessory()) {
            sdkExecutor.execute {
                disconnectControllerLocked("Accesorio USB ausente al regresar a primer plano")
                mux.refreshAccessories(false)
            }
            return
        }

        if (!mux.hasAccessoryPermission()) {
            // No reabrir popup si el usuario lo denegó; si MUX quedó en error o desconectado, refrescar estado
            mux.refreshAccessories(false)
            return
        }

        if (!mux.isHealthy()) {
            Log.w(TAG, "Canal USB/MUX no saludable al volver a primer plano; ejecutando reconexión limpia")
            _uiState.update { it.copy(errorMessage = null) }
            sdkExecutor.execute {
                reconnectUsbAndControllerLocked(
                    reason = "Recuperación automática USB/MUX tras segundo plano",
                    requestPermission = false,
                )
            }
            return
        }

        sdkExecutor.execute {
            val session = activeSession
            if (session == null ||
                session.stopRequested.get() ||
                session.lifecycle.get() == ControllerLifecycleState.STOPPED
            ) {
                Log.i(TAG, "Sesión detenida durante segundo plano con MUX saludable; intentando reconectar")
                _uiState.update { it.copy(errorMessage = null) }
                val service = findUsbSkyControllerService()
                if (service != null) {
                    connectToSkyControllerLocked(service)
                } else {
                    reconnectUsbAndControllerLocked(
                        reason = "Redescubrimiento USB tras segundo plano",
                        requestPermission = false,
                    )
                }
                return@execute
            }

            mainHandler.postDelayed({
                evaluateResumeVideoHealth(token, resumeMs)
            }, RESUME_STREAM_CHECK_DELAY_MS)
        }
    }

    private fun evaluateResumeVideoHealth(token: Int, resumeMs: Long) {
        if (!isAppInForeground || released.get() || recoveryToken.get() != token) return

        val state = _uiState.value
        val metrics = state.videoMetrics
        val freshFramesArrived = metrics.state == VideoStreamState.RECEIVING && metrics.lastFrameTimestampMs >= resumeMs
        if (freshFramesArrived) {
            Log.d(TAG, "Vídeo reanudado correctamente tras regresar de segundo plano")
            return
        }

        val mux = UsbAccessoryMux.get(appContext)
        if (!mux.hasSupportedAccessory()) {
            sdkExecutor.execute {
                disconnectControllerLocked("Cable USB retirado tras regresar")
                mux.refreshAccessories(false)
            }
            return
        }
        if (!mux.hasAccessoryPermission()) {
            mux.refreshAccessories(false)
            return
        }

        if (!mux.isHealthy() ||
            state.skyControllerState == SkyControllerLinkState.DISCONNECTED ||
            state.skyControllerState == SkyControllerLinkState.ERROR
        ) {
            Log.w(TAG, "Enlace SC2/MUX caído al regresar; ejecutando reconexión completa")
            sdkExecutor.execute {
                reconnectUsbAndControllerLocked(
                    reason = "Reconexión automática SC2/MUX tras segundo plano",
                    requestPermission = false,
                )
            }
            return
        }

        val session = activeSession
        if (session != null &&
            !session.stopRequested.get() &&
            session.lifecycle.get() == ControllerLifecycleState.RUNNING &&
            state.bebop2State == Bebop2LinkState.CONNECTED
        ) {
            Log.i(TAG, "Stream detenido/en espera tras segundo plano; reiniciando startVideoStream()")
            sdkExecutor.execute {
                restartVideoStreamLocked(session)
            }
        }

        mainHandler.postDelayed({
            escalateResumeRecoveryIfStalled(token)
        }, RESUME_ESCALATION_DELAY_MS)
    }

    private fun escalateResumeRecoveryIfStalled(token: Int) {
        if (!isAppInForeground || released.get() || recoveryToken.get() != token) return

        val state = _uiState.value
        if (state.videoMetrics.state == VideoStreamState.RECEIVING) return

        val mux = UsbAccessoryMux.get(appContext)
        if (!mux.hasSupportedAccessory() || !mux.hasAccessoryPermission()) return

        Log.w(TAG, "Vídeo/enlace sigue sin recuperar tras pausa (${state.overallPhase}); reiniciando MUX y controlador")
        sdkExecutor.execute {
            reconnectUsbAndControllerLocked(
                reason = "Escalamiento automático tras pausa",
                requestPermission = false,
            )
        }
    }

    private fun cancelRecoveryChecks() {
        recoveryToken.incrementAndGet()
    }

    fun refreshUsbAndMux() {
        if (released.get() || _uiState.value.nativeLoadStatus !is NativeLoadStatus.Loaded) return
        Log.i(TAG, "Comprobando conexión USB/MUX y recuperación de enlace")
        cancelRecoveryChecks()
        ensureDiscoveryInfrastructureStarted()

        val mux = UsbAccessoryMux.get(appContext)
        sdkExecutor.execute {
            if (!mux.hasSupportedAccessory()) {
                _uiState.update { it.copy(errorMessage = null) }
                disconnectControllerLocked("Comprobación USB sin accesorio conectado")
                mux.refreshAccessories(false)
                return@execute
            }

            if (!mux.hasAccessoryPermission()) {
                _uiState.update { it.copy(errorMessage = null) }
                mux.refreshAccessories(true)
                return@execute
            }

            val session = activeSession
            val snapshot = _uiState.value
            val frameAgeMs = if (snapshot.videoMetrics.lastFrameTimestampMs > 0L) {
                SystemClock.elapsedRealtime() - snapshot.videoMetrics.lastFrameTimestampMs
            } else {
                Long.MAX_VALUE
            }
            val alreadyHealthy = session != null &&
                !session.stopRequested.get() &&
                session.lifecycle.get() == ControllerLifecycleState.RUNNING &&
                snapshot.bebop2State == Bebop2LinkState.CONNECTED &&
                snapshot.videoMetrics.state == VideoStreamState.RECEIVING &&
                frameAgeMs <= 2000L &&
                mux.isHealthy()

            if (alreadyHealthy) {
                _uiState.update { it.copy(errorMessage = null) }
                mux.refreshAccessories(false)
                return@execute
            }

            reconnectUsbAndControllerLocked(
                reason = "Comprobación manual USB",
                requestPermission = true,
            )
        }
    }

    fun retryUsbPermission() {
        if (_uiState.value.nativeLoadStatus !is NativeLoadStatus.Loaded) return
        Log.i(TAG, "Reintentando permiso USB Accessory")
        ensureDiscoveryInfrastructureStarted()
        UsbAccessoryMux.get(appContext).requestUsbPermission()
    }

    fun setAutoExposureEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(cameraSettings = it.cameraSettings.copy(autoExposureEnabled = enabled))
        }
        autoExposureController.setAutoEnabled(enabled)
    }

    fun setManualExposureEv(ev: Float) {
        if (_uiState.value.cameraSettings.autoExposureEnabled) {
            setAutoExposureEnabled(false)
        }
        sendExposureInternal(ev)
    }

    fun setWhiteBalanceMode(mode: WhiteBalanceMode) {
        val sdkEnum = when (mode) {
            WhiteBalanceMode.AUTO -> ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_AUTO
            WhiteBalanceMode.DAYLIGHT -> ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_DAYLIGHT
            WhiteBalanceMode.CLOUDY -> ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_CLOUDY
            WhiteBalanceMode.TUNGSTEN -> ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_TUNGSTEN
            WhiteBalanceMode.COOL_WHITE -> ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_AUTOWHITEBALANCESELECTION_TYPE_COOL_WHITE
            WhiteBalanceMode.UNKNOWN -> return
        }

        sdkExecutor.execute {
            val session = activeSession
            val feature = if (session != null && !session.stopRequested.get()) {
                session.controller.featureARDrone3
            } else {
                null
            }
            if (feature == null || _uiState.value.bebop2State != Bebop2LinkState.CONNECTED) {
                return@execute
            }
            val err = feature.sendPictureSettingsAutoWhiteBalanceSelection(sdkEnum)
            Log.i(TAG, "sendPictureSettingsAutoWhiteBalanceSelection($sdkEnum) -> $err")
            if (err != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                _uiState.update {
                    it.copy(errorMessage = "Error cambiando balance de blancos: $err")
                }
            }
        }
    }

    fun setLocalPreviewBrightness(value: Float?) {
        _uiState.update {
            it.copy(
                cameraSettings = it.cameraSettings.copy(
                    localPreviewBrightness = value?.coerceIn(0.05f, 1.0f),
                )
            )
        }
    }

    fun toggleDroneRecording() {
        val current = _uiState.value
        if (current.bebop2State != Bebop2LinkState.CONNECTED) {
            _uiState.update {
                it.copy(
                    droneRecording = it.droneRecording.copy(
                        errorText = "Bebop 2 no conectado para iniciar grabación interna",
                    )
                )
            }
            return
        }
        val rec = current.droneRecording
        if (rec.isRequestPending) {
            Log.w(TAG, "Solicitud de grabación ignorada: operación previa pendiente")
            return
        }
        if (rec.state == DroneRecordState.NOT_AVAILABLE) {
            _uiState.update {
                it.copy(
                    droneRecording = it.droneRecording.copy(
                        errorText = "Grabación no disponible en el Bebop 2",
                    )
                )
            }
            return
        }

        val shouldStart = rec.state != DroneRecordState.RECORDING
        val commandEnum = if (shouldStart) {
            ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_START
        } else {
            ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_STOP
        }

        _uiState.update {
            it.copy(
                droneRecording = it.droneRecording.copy(
                    isRequestPending = true,
                    state = if (shouldStart) DroneRecordState.STARTING else DroneRecordState.STOPPING,
                    errorText = null,
                    lastSavedNotice = null,
                )
            )
        }
        mainHandler.removeCallbacks(recordRequestTimeoutRunnable)
        mainHandler.postDelayed(recordRequestTimeoutRunnable, RECORD_CMD_TIMEOUT_MS)

        sdkExecutor.execute {
            val session = activeSession
            val feature = if (session != null && !session.stopRequested.get()) {
                session.controller.featureARDrone3
            } else {
                null
            }
            if (feature == null) {
                mainHandler.removeCallbacks(recordRequestTimeoutRunnable)
                _uiState.update {
                    it.copy(
                        droneRecording = it.droneRecording.copy(
                            isRequestPending = false,
                            state = DroneRecordState.ERROR,
                            errorText = "Feature ARDrone3 no inicializada",
                        )
                    )
                }
                return@execute
            }

            val err = feature.sendMediaRecordVideoV2(commandEnum)
            Log.i(TAG, "sendMediaRecordVideoV2($commandEnum) -> $err")
            if (err != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                mainHandler.removeCallbacks(recordRequestTimeoutRunnable)
                _uiState.update {
                    it.copy(
                        droneRecording = it.droneRecording.copy(
                            isRequestPending = false,
                            state = DroneRecordState.ERROR,
                            errorText = "No se pudo enviar comando REC: $err",
                        )
                    )
                }
            }
        }
    }

    fun captureDronePhoto() {
        val current = _uiState.value
        if (current.bebop2State != Bebop2LinkState.CONNECTED) {
            _uiState.update {
                it.copy(photoStatus = PhotoStatusState(PhotoCaptureState.NOT_AVAILABLE, "Bebop 2 no conectado"))
            }
            return
        }
        if (current.photoStatus.state == PhotoCaptureState.CAPTURING) {
            return
        }
        _uiState.update {
            it.copy(photoStatus = it.photoStatus.copy(state = PhotoCaptureState.CAPTURING, errorText = null))
        }
        sdkExecutor.execute {
            val session = activeSession
            val feature = if (session != null && !session.stopRequested.get()) {
                session.controller.featureARDrone3
            } else {
                null
            }
            if (feature == null) {
                _uiState.update {
                    it.copy(photoStatus = PhotoStatusState(PhotoCaptureState.ERROR, "ARDrone3 no disponible"))
                }
                return@execute
            }
            val err = feature.sendMediaRecordPictureV2()
            Log.i(TAG, "sendMediaRecordPictureV2() -> $err")
            if (err != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                _uiState.update {
                    it.copy(photoStatus = PhotoStatusState(PhotoCaptureState.ERROR, "Error enviando foto: $err"))
                }
            }
        }
    }

    fun toggleLocalRecording() {
        val localState = _uiState.value.localRecording.state
        if (localState == com.beboppilot.app.sdk.LocalRecordState.IDLE ||
            localState == com.beboppilot.app.sdk.LocalRecordState.ERROR
        ) {
            localMp4Recorder.startRecording()
        } else if (localState != com.beboppilot.app.sdk.LocalRecordState.STOPPING) {
            localMp4Recorder.stopRecording()
        }
    }

    fun refreshDroneGallery() {
        droneMediaRepository.refreshLocalMedia()
        droneMediaRepository.refreshMediaList()
    }

    fun refreshLocalGallery() {
        droneMediaRepository.refreshLocalMedia()
    }

    fun downloadDroneMedia(item: com.beboppilot.app.sdk.DroneMediaItem) {
        droneMediaRepository.downloadMedia(item)
    }

    fun deleteDroneMedia(item: com.beboppilot.app.sdk.DroneMediaItem) {
        val recState = _uiState.value.droneRecording.state
        if (recState == DroneRecordState.RECORDING ||
            recState == DroneRecordState.STARTING ||
            recState == DroneRecordState.STOPPING
        ) {
            _uiState.update {
                it.copy(
                    gallery = it.gallery.copy(
                        errorMessage = "Detén la grabación interna del dron antes de borrar archivos.",
                    )
                )
            }
            return
        }
        flightLogRecorder.recordEvent(_uiState.value, "DRONE_DELETE", item.name)
        droneMediaRepository.deleteMedia(item)
    }

    fun cancelDroneMediaTransfer() {
        droneMediaRepository.cancelActiveOperation()
    }

    fun clearDroneRecordingNotice() {
        _uiState.update {
            it.copy(
                droneRecording = it.droneRecording.copy(
                    errorText = null,
                    lastSavedNotice = null,
                ),
                localRecording = it.localRecording.copy(
                    errorText = null,
                    lastSavedDisplayPath = null,
                ),
                flightSettings = it.flightSettings.copy(
                    flatTrimNotice = null,
                ),
            )
        }
    }

    fun setVideoResolutionProfile(profile: VideoResolutionProfile) {
        val current = _uiState.value
        val droneRecordingActive = current.droneRecording.state == DroneRecordState.RECORDING ||
            current.droneRecording.state == DroneRecordState.STARTING
        val localRecordingActive = current.localRecording.state == LocalRecordState.RECORDING ||
            current.localRecording.state == LocalRecordState.WAITING_KEYFRAME ||
            current.localRecording.state == LocalRecordState.WAITING_CONFIG
        if (droneRecordingActive || localRecordingActive) {
            _uiState.update {
                it.copy(errorMessage = "Detén la grabación antes de cambiar la resolución")
            }
            return
        }
        val sdkEnum = when (profile) {
            VideoResolutionProfile.REC_1080P_STREAM_480P ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORESOLUTIONS_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORESOLUTIONS_TYPE_REC1080_STREAM480
            VideoResolutionProfile.REC_720P_STREAM_720P ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORESOLUTIONS_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORESOLUTIONS_TYPE_REC720_STREAM720
            VideoResolutionProfile.UNKNOWN -> return
        }
        sendDroneFeatureCommand("VideoResolutions($profile)") { it.sendPictureSettingsVideoResolutions(sdkEnum) }
    }

    fun setVideoFramerate(mode: VideoFramerateMode) {
        val sdkEnum = when (mode) {
            VideoFramerateMode.FPS_24 ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_24_FPS
            VideoFramerateMode.FPS_25 ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_25_FPS
            VideoFramerateMode.FPS_30 ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_30_FPS
            VideoFramerateMode.UNKNOWN -> return
        }
        sendDroneFeatureCommand("VideoFramerate($mode)") { it.sendPictureSettingsVideoFramerate(sdkEnum) }
    }

    fun setVideoRecordingMode(mode: VideoRecordingQualityMode) {
        val sdkEnum = when (mode) {
            VideoRecordingQualityMode.QUALITY ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORECORDINGMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORECORDINGMODE_MODE_QUALITY
            VideoRecordingQualityMode.TIME ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORECORDINGMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEORECORDINGMODE_MODE_TIME
            VideoRecordingQualityMode.UNKNOWN -> return
        }
        sendDroneFeatureCommand("VideoRecordingMode($mode)") { it.sendPictureSettingsVideoRecordingMode(sdkEnum) }
    }

    fun setVideoStreamMode(mode: VideoStreamReliabilityMode) {
        val sdkEnum = when (mode) {
            VideoStreamReliabilityMode.LOW_LATENCY ->
                ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_LOW_LATENCY
            VideoStreamReliabilityMode.HIGH_RELIABILITY ->
                ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_HIGH_RELIABILITY
            VideoStreamReliabilityMode.HIGH_RELIABILITY_LOW_FPS ->
                ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_HIGH_RELIABILITY_LOW_FRAMERATE
            VideoStreamReliabilityMode.UNKNOWN -> return
        }
        sendDroneFeatureCommand("VideoStreamMode($mode)") { feature ->
            val err = feature.sendMediaStreamingVideoStreamMode(sdkEnum)
            if (err == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoStreamMode = mode))
                }
            }
            err
        }
    }

    fun setVideoStabilizationMode(mode: VideoStabilizationMode) {
        val sdkEnum = when (mode) {
            VideoStabilizationMode.ROLL_PITCH ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ROLL_PITCH
            VideoStabilizationMode.PITCH ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_PITCH
            VideoStabilizationMode.ROLL ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ROLL
            VideoStabilizationMode.NONE ->
                ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOSTABILIZATIONMODE_MODE_NONE
            VideoStabilizationMode.UNKNOWN -> return
        }
        sendDroneFeatureCommand("VideoStabilization($mode)") { it.sendPictureSettingsVideoStabilizationMode(sdkEnum) }
    }

    fun setSaturation(value: Float) {
        val target = _uiState.value.cameraSettings.saturation.coerceTarget(value)
        sendDroneFeatureCommand("Saturation($target)") { it.sendPictureSettingsSaturationSelection(target) }
    }

    fun setVideoAutorecordEnabled(enabled: Boolean) {
        val storageId = _uiState.value.cameraSettings.videoAutorecordMassStorageId
            .coerceIn(0, 255)
            .toByte()
        val byteEnabled: Byte = if (enabled) 1 else 0
        sendDroneFeatureCommand("VideoAutorecord(enabled=$enabled,storage=$storageId)") {
            it.sendPictureSettingsVideoAutorecordSelection(byteEnabled, storageId)
        }
    }

    // --- Fase 1 FreeFlight Pro: Ajustes de Vuelo, Velocidad de Giro/Inclinación y Límites ---

    fun setMaxRotationSpeed(degPerSec: Float) {
        val target = _uiState.value.flightSettings.maxRotationSpeed.coerceTarget(degPerSec)
        sendDroneFeatureCommand("MaxRotationSpeed($target)") { it.sendSpeedSettingsMaxRotationSpeed(target) }
    }

    fun setMaxTilt(deg: Float) {
        val target = _uiState.value.flightSettings.maxTilt.coerceTarget(deg)
        sendDroneFeatureCommand("MaxTilt($target)") { it.sendPilotingSettingsMaxTilt(target) }
    }

    fun setMaxPitchRollRotationSpeed(degPerSec: Float) {
        val target = _uiState.value.flightSettings.maxPitchRollRotationSpeed.coerceTarget(degPerSec)
        sendDroneFeatureCommand("MaxPitchRollRotationSpeed($target)") {
            it.sendSpeedSettingsMaxPitchRollRotationSpeed(target)
        }
    }

    fun setMaxVerticalSpeed(metersPerSec: Float) {
        val target = _uiState.value.flightSettings.maxVerticalSpeed.coerceTarget(metersPerSec)
        sendDroneFeatureCommand("MaxVerticalSpeed($target)") { it.sendSpeedSettingsMaxVerticalSpeed(target) }
    }

    fun setMaxAltitude(meters: Float) {
        val target = _uiState.value.flightSettings.maxAltitude.coerceTarget(meters)
        sendDroneFeatureCommand("MaxAltitude($target)") { it.sendPilotingSettingsMaxAltitude(target) }
    }

    fun setMaxDistance(meters: Float) {
        val target = _uiState.value.flightSettings.maxDistance.coerceTarget(meters)
        sendDroneFeatureCommand("MaxDistance($target)") { it.sendPilotingSettingsMaxDistance(target) }
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        sendDroneFeatureCommand("NoFlyOverMaxDistance($enabled)") {
            it.sendPilotingSettingsNoFlyOverMaxDistance(if (enabled) 1 else 0)
        }
    }

    fun setBankedTurnEnabled(enabled: Boolean) {
        sendDroneFeatureCommand("BankedTurn($enabled)") {
            it.sendPilotingSettingsBankedTurn(if (enabled) 1 else 0)
        }
    }

    fun setHullProtectionPresent(present: Boolean) {
        sendDroneFeatureCommand("HullProtection($present)") {
            it.sendSpeedSettingsHullProtection(if (present) 1 else 0)
        }
    }

    fun setReturnHomeType(type: ReturnHomeType) {
        val sdkEnum = when (type) {
            ReturnHomeType.TAKEOFF -> ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF
            ReturnHomeType.PILOT -> ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_PILOT
            ReturnHomeType.FOLLOWEE -> ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_FOLLOWEE
            ReturnHomeType.UNKNOWN -> return
        }
        sendDroneFeatureCommand("HomeType($type)") { it.sendGPSSettingsHomeType(sdkEnum) }
    }

    fun setReturnHomeDelay(seconds: Int) {
        val clamped = seconds.coerceIn(0, 120).toShort()
        sendDroneFeatureCommand("ReturnHomeDelay($clamped)") { it.sendGPSSettingsReturnHomeDelay(clamped) }
    }

    fun applyFlightPreset(preset: FlightPreset) {
        val fs = _uiState.value.flightSettings
        val tilt = fs.maxTilt.coerceTarget(preset.targetTiltDeg)
        val yaw = fs.maxRotationSpeed.coerceTarget(preset.targetYawDegSec)
        val pitchRollRate = fs.maxPitchRollRotationSpeed.coerceTarget(preset.targetPitchRollRateDegSec)
        val vertical = fs.maxVerticalSpeed.coerceTarget(preset.targetVerticalMs)

        sendDroneFeatureCommand("FlightPreset(${preset.name})") { feature ->
            val results = listOf(
                feature.sendPilotingSettingsMaxTilt(tilt),
                feature.sendSpeedSettingsMaxRotationSpeed(yaw),
                feature.sendSpeedSettingsMaxPitchRollRotationSpeed(pitchRollRate),
                feature.sendSpeedSettingsMaxVerticalSpeed(vertical),
                feature.sendPilotingSettingsBankedTurn(if (preset.bankedTurn) 1 else 0),
            )
            results.firstOrNull { it != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK }
                ?: ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK
        }
    }

    fun sendFlatTrim() {
        val flying = _uiState.value.bebop2FlyingState
        if (flying != null && flying != "ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED" &&
            flying != "ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_EMERGENCY"
        ) {
            _uiState.update {
                it.copy(
                    flightSettings = it.flightSettings.copy(
                        flatTrimNotice = "Flat Trim solo disponible con el dron aterrizado",
                    )
                )
            }
            return
        }
        sendDroneFeatureCommand("FlatTrim()") { it.sendPilotingFlatTrim() }
    }

    fun startMagnetoCalibration() {
        val current = _uiState.value
        val flying = current.bebop2FlyingState
        if (flying != null &&
            flying != "ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED" &&
            flying != "ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_EMERGENCY"
        ) {
            _uiState.update {
                it.copy(
                    flightSettings = it.flightSettings.copy(
                        magnetoCalibration = it.flightSettings.magnetoCalibration.copy(
                            statusNotice = "Aterriza el dron antes de calibrar la brújula.",
                        ),
                    ),
                )
            }
            return
        }
        sendCommonFeatureCommand("MagnetoCalibration(start)") {
            it.sendCalibrationMagnetoCalibration(1)
        }
    }

    fun abortMagnetoCalibration() {
        sendCommonFeatureCommand("MagnetoCalibration(abort)") {
            it.sendCalibrationMagnetoCalibration(0)
        }
    }

    fun exportLatestFlightLog() {
        flightLogRecorder.exportLatestToDownloads { /* status callback updates uiState.flightLog */ }
    }

    fun setControllerStickExpo(axisIdOrAll: Int, expo: StickExpoMode) {
        val sdkExpo = when (expo) {
            StickExpoMode.LINEAR -> ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.LINEAR
            StickExpoMode.EXPO_0 -> ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_0
            StickExpoMode.EXPO_1 -> ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_1
            StickExpoMode.EXPO_2 -> ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_2
            StickExpoMode.EXPO_4 -> ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_4
            StickExpoMode.UNKNOWN -> return
        }
        sdkExecutor.execute {
            val session = activeSession
            if (session == null || session.stopRequested.get()) return@execute
            if (_uiState.value.skyControllerState != SkyControllerLinkState.CONNECTED) return@execute
            val mapper = session.controller.featureMapper ?: return@execute
            val productId = _uiState.value.controllerExpo.activeProductId
            val err = mapper.sendSetExpo(productId, axisIdOrAll, sdkExpo)
            Log.i(TAG, "mapper.sendSetExpo(product=$productId, axis=$axisIdOrAll, expo=$sdkExpo) -> $err")
            if (err != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                _uiState.update {
                    it.copy(errorMessage = "Error configurando curva del mando: $err")
                }
            }
        }
    }

    private inline fun sendDroneFeatureCommand(
        actionLabel: String,
        crossinline block: (ARFeatureARDrone3) -> ARCONTROLLER_ERROR_ENUM,
    ) {
        sdkExecutor.execute {
            val session = activeSession
            if (session == null || session.stopRequested.get()) return@execute
            val feature = session.controller.featureARDrone3 ?: return@execute
            if (_uiState.value.bebop2State != Bebop2LinkState.CONNECTED) return@execute

            val err = block(feature)
            Log.i(TAG, "$actionLabel -> $err")
            if (err == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                flightLogRecorder.recordEvent(_uiState.value, "SETTING", actionLabel)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Error en $actionLabel: $err")
                }
            }
        }
    }

    private inline fun sendCommonFeatureCommand(
        actionLabel: String,
        crossinline block: (ARFeatureCommon) -> ARCONTROLLER_ERROR_ENUM,
    ) {
        sdkExecutor.execute {
            val session = activeSession
            if (session == null || session.stopRequested.get()) return@execute
            val feature = session.controller.featureCommon ?: return@execute
            if (_uiState.value.bebop2State != Bebop2LinkState.CONNECTED) return@execute

            val err = block(feature)
            Log.i(TAG, "$actionLabel -> $err")
            if (err == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                flightLogRecorder.recordEvent(_uiState.value, "COMMON_CMD", actionLabel)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Error en $actionLabel: $err")
                }
            }
        }
    }

    private fun parseBoundedSetting(
        args: Map<String, Any>,
        currentKey: String,
        minKey: String,
        maxKey: String,
    ): BoundedFloatSetting? {
        val cur = (args[currentKey] as? Number)?.toFloat() ?: return null
        val min = (args[minKey] as? Number)?.toFloat() ?: return null
        val max = (args[maxKey] as? Number)?.toFloat() ?: return null
        if (min > max) return null
        return BoundedFloatSetting(
            current = cur.coerceIn(min, max),
            min = min,
            max = max,
            known = true,
        )
    }

    private fun sendExposureInternal(rawEv: Float) {
        sdkExecutor.execute {
            val session = activeSession
            if (session == null || session.stopRequested.get()) return@execute
            val feature = session.controller.featureARDrone3 ?: return@execute
            if (_uiState.value.bebop2State != Bebop2LinkState.CONNECTED) return@execute

            val cam = _uiState.value.cameraSettings
            val bounded = rawEv.coerceIn(cam.exposureMin, cam.exposureMax)
            val rounded = round(bounded * 100f) / 100f
            val err = feature.sendPictureSettingsExpositionSelection(rounded)
            Log.i(TAG, "sendPictureSettingsExpositionSelection($rounded) -> $err")
            if (err != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                _uiState.update {
                    it.copy(errorMessage = "Error ajustando exposición EV: $err")
                }
            }
        }
    }

    private fun handleCommandReceived(
        commandKey: ARCONTROLLER_DICTIONARY_KEY_ENUM,
        elementDictionary: ARControllerDictionary,
    ) {
        // Comandos tipo lista (múltiples elementos en el diccionario)
        when (commandKey) {
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED -> {
                val parsed = elementDictionary.values.mapNotNull { entry ->
                    val nameRaw = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME] as? Number)?.toInt()
                        ?: return@mapNotNull null
                    val stateRaw = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORSTATE] as? Number)?.toInt()
                        ?: 0
                    val sensorEnum = ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.getFromValue(nameRaw)
                    val friendly = when (sensorEnum) {
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_IMU -> "IMU"
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_BAROMETER -> "Barómetro"
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ULTRASOUND -> "Ultrasonido"
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_GPS -> "GPS"
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_MAGNETOMETER -> "Magnetómetro"
                        ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_VERTICAL_CAMERA -> "Cámara vertical"
                        else -> sensorEnum?.name ?: "Sensor #$nameRaw"
                    }
                    SensorItemState(name = friendly, ok = stateRaw != 0)
                }
                if (parsed.isNotEmpty()) {
                    _uiState.update {
                        it.copy(telemetry = it.telemetry.copy(sensors = parsed))
                    }
                    val koNames = parsed.filter { !it.ok }.map { it.name }
                    if (koNames.isNotEmpty()) {
                        flightLogRecorder.recordEvent(
                            _uiState.value,
                            "SENSOR_KO",
                            koNames.joinToString(", "),
                        )
                    }
                }
                return
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED -> {
                val entry = elementDictionary.values.firstOrNull() ?: return
                val totalMb = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_SIZE] as? Number)?.toLong()
                val usedMb = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_USED_SIZE] as? Number)?.toLong()
                val full = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_FULL] as? Number)?.toInt() == 1
                val freeMb = if (totalMb != null && usedMb != null) (totalMb - usedMb).coerceAtLeast(0L) else null
                _uiState.update {
                    it.copy(
                        droneRecording = it.droneRecording.copy(
                            massStorageTotalMb = totalMb,
                            massStorageUsedMb = usedMb,
                            massStorageFreeMb = freeMb ?: it.droneRecording.massStorageFreeMb,
                            isMemoryFull = full || it.droneRecording.isMemoryFull,
                        )
                    )
                }
                return
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED -> {
                val entry = elementDictionary.values.firstOrNull() ?: return
                val freeMb = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED_FREE_SPACE] as? Number)?.toLong()
                val recSec = (entry[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED_REC_TIME] as? Number)?.toLong()
                _uiState.update {
                    it.copy(
                        droneRecording = it.droneRecording.copy(
                            massStorageFreeMb = freeMb ?: it.droneRecording.massStorageFreeMb,
                            remainingRecTimeSec = recSec ?: it.droneRecording.remainingRecTimeSec,
                        )
                    )
                }
                return
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MAPPER_AXISMAPPINGITEM -> {
                val activeProd = _uiState.value.controllerExpo.activeProductId
                var yawAxis = _uiState.value.controllerExpo.yawAxisId
                var pitchAxis = _uiState.value.controllerExpo.pitchAxisId
                var rollAxis = _uiState.value.controllerExpo.rollAxisId
                var gazAxis = _uiState.value.controllerExpo.gazAxisId

                for (entry in elementDictionary.values) {
                    val product = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_AXISMAPPINGITEM_PRODUCT] as? Number)?.toShort()
                        ?: continue
                    if (activeProd != 0.toShort() && product != activeProd) continue
                    val actionRaw = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_AXISMAPPINGITEM_ACTION] as? Number)?.toInt()
                        ?: continue
                    val axis = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_AXISMAPPINGITEM_AXIS] as? Number)?.toInt()
                        ?: continue
                    val validAxis = axis.takeIf { it >= 0 }
                    when (ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM.getFromValue(actionRaw)) {
                        ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM.YAW -> yawAxis = validAxis
                        ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM.PITCH -> pitchAxis = validAxis
                        ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM.ROLL -> rollAxis = validAxis
                        ARCOMMANDS_MAPPER_AXIS_ACTION_ENUM.GAZ -> gazAxis = validAxis
                        else -> Unit
                    }
                }
                _uiState.update {
                    it.copy(
                        controllerExpo = it.controllerExpo.copy(
                            yawAxisId = yawAxis,
                            pitchAxisId = pitchAxis,
                            rollAxisId = rollAxis,
                            gazAxisId = gazAxis,
                        )
                    )
                }
                return
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MAPPER_EXPOMAPITEM -> {
                val activeProd = _uiState.value.controllerExpo.activeProductId
                val updatedExpos = _uiState.value.controllerExpo.axisExpos.toMutableMap()
                for (entry in elementDictionary.values) {
                    val product = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_EXPOMAPITEM_PRODUCT] as? Number)?.toShort()
                        ?: continue
                    if (activeProd != 0.toShort() && product != activeProd) continue
                    val axis = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_EXPOMAPITEM_AXIS] as? Number)?.toInt()
                        ?: continue
                    val expoRaw = (entry[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_EXPOMAPITEM_EXPO] as? Number)?.toInt()
                        ?: continue
                    val mapped = when (ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.getFromValue(expoRaw)) {
                        ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.LINEAR -> StickExpoMode.LINEAR
                        ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_0 -> StickExpoMode.EXPO_0
                        ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_1 -> StickExpoMode.EXPO_1
                        ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_2 -> StickExpoMode.EXPO_2
                        ARCOMMANDS_MAPPER_EXPO_TYPE_ENUM.EXPO_4 -> StickExpoMode.EXPO_4
                        else -> StickExpoMode.UNKNOWN
                    }
                    updatedExpos[axis] = mapped
                }
                _uiState.update {
                    it.copy(controllerExpo = it.controllerExpo.copy(axisExpos = updatedExpos))
                }
                return
            }

            else -> Unit
        }

        val args = elementDictionary[ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY] ?: return

        when (commandKey) {
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_SKYCONTROLLER_SKYCONTROLLERSTATE_BATTERYCHANGED -> {
                val percent = (args[ARFeatureSkyController.ARCONTROLLER_DICTIONARY_KEY_SKYCONTROLLER_SKYCONTROLLERSTATE_BATTERYCHANGED_PERCENT] as? Number)?.toInt()
                if (percent != null) {
                    _uiState.update { it.copy(skyControllerBatteryPercent = percent) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_SKYCONTROLLER_WIFISTATE_WIFISIGNALCHANGED -> {
                val level = (args[ARFeatureSkyController.ARCONTROLLER_DICTIONARY_KEY_SKYCONTROLLER_WIFISTATE_WIFISIGNALCHANGED_LEVEL] as? Number)?.toInt()
                if (level != null) {
                    _uiState.update {
                        it.copy(telemetry = it.telemetry.copy(skyControllerSignalLevel = level))
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED -> {
                val percent = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT] as? Number)?.toInt()
                if (percent != null) {
                    _uiState.update { it.copy(bebop2BatteryPercent = percent) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_WIFISIGNALCHANGED -> {
                val rssi = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_WIFISIGNALCHANGED_RSSI] as? Number)?.toInt()
                if (rssi != null) {
                    _uiState.update {
                        it.copy(telemetry = it.telemetry.copy(droneWifiRssiDbm = rssi))
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_OVERHEATSTATE_OVERHEATREGULATIONCHANGED -> {
                val regRaw = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_OVERHEATSTATE_OVERHEATREGULATIONCHANGED_REGULATIONTYPE] as? Number)?.toInt()
                if (regRaw != null) {
                    val label = if (regRaw == 0) "Normal" else "Regulación térmica activa ($regRaw)"
                    _uiState.update {
                        it.copy(telemetry = it.telemetry.copy(overheatRegulation = label))
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE] as? Number)?.toInt()
                if (raw != null) {
                    val flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(raw)
                    val prev = _uiState.value.bebop2FlyingState
                    if (flyingState?.name != prev) {
                        _uiState.update { it.copy(bebop2FlyingState = flyingState?.name) }
                        flightLogRecorder.recordEvent(
                            _uiState.value,
                            "FLYING_STATE",
                            flyingState?.name?.removePrefix("ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_") ?: "$raw",
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED -> {
                val name = args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED_NAME] as? String
                if (!name.isNullOrBlank()) {
                    _uiState.update { it.copy(bebop2Name = name) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED -> {
                val software = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED_SOFTWARE] as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val hardware = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED_HARDWARE] as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (software != null || hardware != null) {
                    val firmware = BebopFirmwareInfo(
                        softwareVersion = software,
                        hardwareVersion = hardware,
                    )
                    Log.i(
                        TAG,
                        "Firmware Bebop 2 detectado: sw=${firmware.softwareVersion ?: "N/D"} hw=${firmware.hardwareVersion ?: "N/D"} (>=3.4.0=${firmware.supportsVideoResolutionControls})",
                    )
                    _uiState.update { it.copy(bebop2Firmware = firmware) }
                }
            }

            // --- Fase B: Exposición y Balance de Blancos ---
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_EXPOSITIONCHANGED -> {
                val value = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_EXPOSITIONCHANGED_VALUE] as? Number)?.toFloat()
                val min = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_EXPOSITIONCHANGED_MIN] as? Number)?.toFloat()
                val max = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_EXPOSITIONCHANGED_MAX] as? Number)?.toFloat()
                if (value != null && min != null && max != null && min <= max) {
                    autoExposureController.updateDroneExposureBounds(value, min, max)
                    _uiState.update {
                        it.copy(
                            cameraSettings = it.cameraSettings.copy(
                                exposureValue = value.coerceIn(min, max),
                                exposureMin = min,
                                exposureMax = max,
                                exposureKnown = true,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE] as? Number)?.toInt()
                if (raw != null) {
                    val wbEnum = ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.getFromValue(raw)
                    val mapped = when (wbEnum) {
                        ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_AUTO -> WhiteBalanceMode.AUTO
                        ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_DAYLIGHT -> WhiteBalanceMode.DAYLIGHT
                        ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_CLOUDY -> WhiteBalanceMode.CLOUDY
                        ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_TUNGSTEN -> WhiteBalanceMode.TUNGSTEN
                        ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_AUTOWHITEBALANCECHANGED_TYPE_COOL_WHITE -> WhiteBalanceMode.COOL_WHITE
                        else -> WhiteBalanceMode.UNKNOWN
                    }
                    _uiState.update {
                        it.copy(
                            cameraSettings = it.cameraSettings.copy(
                                whiteBalanceMode = mapped,
                                whiteBalanceKnown = mapped != WhiteBalanceMode.UNKNOWN,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE] as? Number)?.toInt()
                val mapped = when (raw?.let { ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_REC1080_STREAM480 ->
                        VideoResolutionProfile.REC_1080P_STREAM_480P
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORESOLUTIONSCHANGED_TYPE_REC720_STREAM720 ->
                        VideoResolutionProfile.REC_720P_STREAM_720P
                    else -> VideoResolutionProfile.UNKNOWN
                }
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoResolution = mapped))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE] as? Number)?.toInt()
                val mapped = when (raw?.let { ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_24_FPS -> VideoFramerateMode.FPS_24
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_25_FPS -> VideoFramerateMode.FPS_25
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOFRAMERATECHANGED_FRAMERATE_30_FPS -> VideoFramerateMode.FPS_30
                    else -> VideoFramerateMode.UNKNOWN
                }
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoFramerate = mapped))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE] as? Number)?.toInt()
                val mapped = when (raw?.let { ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_QUALITY -> VideoRecordingQualityMode.QUALITY
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEORECORDINGMODECHANGED_MODE_TIME -> VideoRecordingQualityMode.TIME
                    else -> VideoRecordingQualityMode.UNKNOWN
                }
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoRecordingMode = mapped))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE] as? Number)?.toInt()
                val mapped = when (raw?.let { ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_LOW_LATENCY -> VideoStreamReliabilityMode.LOW_LATENCY
                    ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_HIGH_RELIABILITY -> VideoStreamReliabilityMode.HIGH_RELIABILITY
                    ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMINGSTATE_VIDEOSTREAMMODECHANGED_MODE_HIGH_RELIABILITY_LOW_FRAMERATE -> VideoStreamReliabilityMode.HIGH_RELIABILITY_LOW_FPS
                    else -> VideoStreamReliabilityMode.UNKNOWN
                }
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoStreamMode = mapped))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE] as? Number)?.toInt()
                val mapped = when (raw?.let { ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ROLL_PITCH -> VideoStabilizationMode.ROLL_PITCH
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_PITCH -> VideoStabilizationMode.PITCH
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ROLL -> VideoStabilizationMode.ROLL
                    ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGSSTATE_VIDEOSTABILIZATIONMODECHANGED_MODE_NONE -> VideoStabilizationMode.NONE
                    else -> VideoStabilizationMode.UNKNOWN
                }
                _uiState.update {
                    it.copy(cameraSettings = it.cameraSettings.copy(videoStabilization = mapped))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_SATURATIONCHANGED -> {
                val value = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_SATURATIONCHANGED_VALUE] as? Number)?.toFloat()
                val min = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_SATURATIONCHANGED_MIN] as? Number)?.toFloat()
                val max = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_SATURATIONCHANGED_MAX] as? Number)?.toFloat()
                if (value != null && min != null && max != null && min <= max) {
                    _uiState.update {
                        it.copy(
                            cameraSettings = it.cameraSettings.copy(
                                saturation = BoundedFloatSetting(
                                    current = value.coerceIn(min, max),
                                    min = min,
                                    max = max,
                                    known = true,
                                )
                            )
                        )
                    }
                }
            }

            // --- Fase C: Grabación interna Bebop 2 y Fotografía ---
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2 -> {
                val stateRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE] as? Number)?.toInt()
                val errRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR] as? Number)?.toInt()
                val stateEnum = stateRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM.getFromValue(it) }
                val errEnum = errRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.getFromValue(it) }

                mainHandler.removeCallbacks(recordRequestTimeoutRunnable)
                val mappedState = when (stateEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STARTED -> DroneRecordState.RECORDING
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_STOPPED -> DroneRecordState.STOPPED
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_STATE_NOTAVAILABLE -> DroneRecordState.NOT_AVAILABLE
                    else -> DroneRecordState.UNKNOWN
                }

                val isMemFull = errEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_MEMORYFULL
                val isLowBatt = errEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_LOWBATTERY
                val errMessage = when (errEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_MEMORYFULL -> "Memoria interna del Bebop 2 llena"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_LOWBATTERY -> "Batería insuficiente en el Bebop 2 para grabar"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_CAMERA_KO -> "Cámara del Bebop 2 fuera de servicio"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_VIDEOSTATECHANGEDV2_ERROR_UNKNOWN -> "Error genérico de grabación en Bebop 2"
                    else -> null
                }

                if (mappedState == DroneRecordState.RECORDING) {
                    startRecordDurationTicker()
                } else {
                    stopRecordDurationTicker()
                }

                _uiState.update { current ->
                    val existingStart = current.droneRecording.startedAtElapsedMs
                    val newStart = if (mappedState == DroneRecordState.RECORDING) {
                        if (existingStart > 0L) existingStart else SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
                    val newDur = if (mappedState == DroneRecordState.RECORDING && newStart > 0L) {
                        ((SystemClock.elapsedRealtime() - newStart) / 1000L).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    current.copy(
                        droneRecording = current.droneRecording.copy(
                            state = mappedState,
                            isRequestPending = false,
                            startedAtElapsedMs = newStart,
                            durationSec = newDur,
                            errorText = errMessage ?: if (mappedState == DroneRecordState.RECORDING) null else current.droneRecording.errorText,
                            isMemoryFull = isMemFull,
                            isLowBattery = isLowBatt,
                        )
                    )
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED -> {
                val eventRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT] as? Number)?.toInt()
                val errRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR] as? Number)?.toInt()
                val eventEnum = eventRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_ENUM.getFromValue(it) }
                val errEnum = errRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.getFromValue(it) }

                mainHandler.removeCallbacks(recordRequestTimeoutRunnable)
                val isMemFull = errEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_MEMORYFULL
                val isLowBatt = errEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_LOWBATTERY

                val errorMsg = when (errEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_MEMORYFULL -> "Memoria llena en Bebop 2"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_LOWBATTERY -> "Batería baja en Bebop 2: grabación detenida"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_AUTOSTOPPED -> "Grabación detenida automáticamente por el dron"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_BUSY -> "Cámara ocupada en Bebop 2"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_NOTAVAILABLE -> "Grabación no disponible"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_ERROR_UNKNOWN -> "Error al grabar video en Bebop 2"
                    else -> null
                }

                when (eventEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_START -> {
                        startRecordDurationTicker()
                        _uiState.update {
                            val startTs = it.droneRecording.startedAtElapsedMs.takeIf { ts -> ts > 0L }
                                ?: SystemClock.elapsedRealtime()
                            it.copy(
                                droneRecording = it.droneRecording.copy(
                                    state = DroneRecordState.RECORDING,
                                    isRequestPending = false,
                                    startedAtElapsedMs = startTs,
                                    errorText = errorMsg,
                                    isMemoryFull = isMemFull,
                                    isLowBattery = isLowBatt,
                                )
                            )
                        }
                    }

                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_STOP -> {
                        stopRecordDurationTicker()
                        _uiState.update {
                            it.copy(
                                droneRecording = it.droneRecording.copy(
                                    state = DroneRecordState.STOPPED,
                                    isRequestPending = false,
                                    startedAtElapsedMs = 0L,
                                    durationSec = 0L,
                                    errorText = errorMsg,
                                    isMemoryFull = isMemFull,
                                    isLowBattery = isLowBatt,
                                    lastSavedNotice = if (errorMsg == null) "Video guardado en memoria interna del Bebop 2" else null,
                                )
                            )
                        }
                    }

                    ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_VIDEOEVENTCHANGED_EVENT_FAILED -> {
                        stopRecordDurationTicker()
                        _uiState.update {
                            it.copy(
                                droneRecording = it.droneRecording.copy(
                                    state = DroneRecordState.STOPPED,
                                    isRequestPending = false,
                                    startedAtElapsedMs = 0L,
                                    durationSec = 0L,
                                    errorText = errorMsg ?: "Fallo iniciando grabación en Bebop 2",
                                    isMemoryFull = isMemFull,
                                    isLowBattery = isLowBatt,
                                )
                            )
                        }
                    }

                    else -> Unit
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2 -> {
                val stateRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE] as? Number)?.toInt()
                val errRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR] as? Number)?.toInt()
                val stateEnum = stateRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_ENUM.getFromValue(it) }
                val errEnum = errRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_ENUM.getFromValue(it) }

                val mapped = when (stateEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_READY -> PhotoCaptureState.IDLE
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_BUSY -> PhotoCaptureState.BUSY
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_STATE_NOTAVAILABLE -> PhotoCaptureState.NOT_AVAILABLE
                    else -> PhotoCaptureState.IDLE
                }
                val errMsg = when (errEnum) {
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_MEMORYFULL -> "Memoria llena en Bebop 2"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_LOWBATTERY -> "Batería insuficiente para foto"
                    ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDSTATE_PICTURESTATECHANGEDV2_ERROR_CAMERA_KO -> "Cámara no disponible"
                    else -> null
                }
                _uiState.update {
                    it.copy(photoStatus = it.photoStatus.copy(state = mapped, errorText = errMsg))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED -> {
                val eventRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT] as? Number)?.toInt()
                val errRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR] as? Number)?.toInt()
                val eventEnum = eventRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_ENUM.getFromValue(it) }
                val errEnum = errRaw?.let { ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.getFromValue(it) }

                if (eventEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_TAKEN) {
                    _uiState.update {
                        it.copy(
                            photoStatus = PhotoStatusState(
                                state = PhotoCaptureState.TAKEN,
                                errorText = null,
                                lastCaptureTimestampMs = SystemClock.elapsedRealtime(),
                            )
                        )
                    }
                } else if (eventEnum == ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_EVENT_FAILED) {
                    val errText = when (errEnum) {
                        ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_MEMORYFULL -> "Memoria llena en Bebop 2"
                        ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_LOWBATTERY -> "Batería insuficiente para capturar foto"
                        ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_BUSY -> "Cámara ocupada"
                        else -> "No se pudo capturar la foto (${errEnum?.name ?: "error"})"
                    }
                    _uiState.update {
                        it.copy(photoStatus = PhotoStatusState(state = PhotoCaptureState.ERROR, errorText = errText))
                    }
                }
            }

            // --- Telemetría real (sin valores simulados) ---
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED -> {
                val alt = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALTITUDECHANGED_ALTITUDE] as? Number)?.toDouble()
                if (alt != null) {
                    _uiState.update { it.copy(telemetry = it.telemetry.copy(altitudeMeters = alt)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED -> {
                val sx = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDX] as? Number)?.toFloat()
                val sy = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDY] as? Number)?.toFloat()
                val sz = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_SPEEDCHANGED_SPEEDZ] as? Number)?.toFloat()
                _uiState.update {
                    it.copy(telemetry = it.telemetry.copy(speedX = sx, speedY = sy, speedZ = sz))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED -> {
                val roll = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_ROLL] as? Number)?.toFloat()
                val pitch = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_PITCH] as? Number)?.toFloat()
                val yaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_YAW] as? Number)?.toFloat()
                _uiState.update {
                    it.copy(telemetry = it.telemetry.copy(rollRad = roll, pitchRad = pitch, yawRad = yaw))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED -> {
                val lat = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_LATITUDE] as? Number)?.toDouble()
                val lon = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_LONGITUDE] as? Number)?.toDouble()
                val gpsAlt = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_ALTITUDE] as? Number)?.toDouble()
                // Parrot envía 500.0 como valor inválido cuando no hay fix GPS
                val validCoords = lat != null && lon != null && lat != 500.0 && lon != 500.0
                _uiState.update {
                    it.copy(
                        telemetry = it.telemetry.copy(
                            latitude = if (validCoords) lat else null,
                            longitude = if (validCoords) lon else null,
                            gpsAltitude = if (validCoords) gpsAlt else null,
                        )
                    )
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_GPSFIXSTATECHANGED -> {
                val fixed = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_GPSFIXSTATECHANGED_FIXED] as? Number)?.toInt()
                if (fixed != null) {
                    _uiState.update { it.copy(telemetry = it.telemetry.copy(gpsFixed = fixed != 0)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_NUMBEROFSATELLITECHANGED -> {
                val sats = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSTATE_NUMBEROFSATELLITECHANGED_NUMBEROFSATELLITE] as? Number)?.toInt()
                if (sats != null) {
                    _uiState.update { it.copy(telemetry = it.telemetry.copy(gpsSatellites = sats)) }
                }
            }

            // --- Fase 1 FreeFlight Pro: Velocidades, Límites, RTH, Alertas y Curva SC2 ---
            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_MAPPER_ACTIVEPRODUCT -> {
                val prod = (args[ARFeatureMapper.ARCONTROLLER_DICTIONARY_KEY_MAPPER_ACTIVEPRODUCT_PRODUCT] as? Number)?.toShort()
                if (prod != null) {
                    _uiState.update { it.copy(controllerExpo = it.controllerExpo.copy(activeProductId = prod)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXROTATIONSPEEDCHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxRotationSpeed = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXPITCHROLLROTATIONSPEEDCHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXPITCHROLLROTATIONSPEEDCHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXPITCHROLLROTATIONSPEEDCHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXPITCHROLLROTATIONSPEEDCHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxPitchRollRotationSpeed = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXVERTICALSPEEDCHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXVERTICALSPEEDCHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXVERTICALSPEEDCHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_MAXVERTICALSPEEDCHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxVerticalSpeed = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_HULLPROTECTIONCHANGED -> {
                val present = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_SPEEDSETTINGSSTATE_HULLPROTECTIONCHANGED_PRESENT] as? Number)?.toInt()
                if (present != null) {
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                hullProtectionPresent = present != 0,
                                hullProtectionKnown = true,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXTILTCHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXTILTCHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXTILTCHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXTILTCHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxTilt = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXALTITUDECHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXALTITUDECHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXALTITUDECHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXALTITUDECHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxAltitude = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXDISTANCECHANGED -> {
                parseBoundedSetting(
                    args,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXDISTANCECHANGED_CURRENT,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXDISTANCECHANGED_MIN,
                    ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_MAXDISTANCECHANGED_MAX,
                )?.let { s ->
                    _uiState.update { it.copy(flightSettings = it.flightSettings.copy(maxDistance = s)) }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_NOFLYOVERMAXDISTANCECHANGED -> {
                val geofence = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_NOFLYOVERMAXDISTANCECHANGED_SHOULDNOTFLYOVER] as? Number)?.toInt()
                if (geofence != null) {
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                geofenceEnabled = geofence != 0,
                                geofenceKnown = true,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_BANKEDTURNCHANGED -> {
                val banked = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSETTINGSSTATE_BANKEDTURNCHANGED_STATE] as? Number)?.toInt()
                if (banked != null) {
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                bankedTurnEnabled = banked != 0,
                                bankedTurnKnown = true,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_HOMETYPECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_HOMETYPECHANGED_TYPE] as? Number)?.toInt()
                if (raw != null) {
                    val mapped = when (ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.getFromValue(raw)) {
                        ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_TAKEOFF -> ReturnHomeType.TAKEOFF
                        ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_PILOT -> ReturnHomeType.PILOT
                        ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_ENUM.ARCOMMANDS_ARDRONE3_GPSSETTINGS_HOMETYPE_TYPE_FOLLOWEE -> ReturnHomeType.FOLLOWEE
                        else -> ReturnHomeType.UNKNOWN
                    }
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                returnHomeType = mapped,
                                returnHomeTypeKnown = mapped != ReturnHomeType.UNKNOWN,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RETURNHOMEDELAYCHANGED -> {
                val delay = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_GPSSETTINGSSTATE_RETURNHOMEDELAYCHANGED_DELAY] as? Number)?.toInt()
                if (delay != null) {
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                returnHomeDelaySec = delay,
                                returnHomeDelayKnown = true,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLATTRIMCHANGED -> {
                _uiState.update {
                    it.copy(flightSettings = it.flightSettings.copy(flatTrimNotice = "Flat Trim completado"))
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED -> {
                val raw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE] as? Number)?.toInt()
                val label = when (raw?.let { ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_NONE -> null
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_USER -> "Emergencia de usuario"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_CUT_OUT -> "Corte de motores"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_CRITICAL_BATTERY -> "Batería crítica"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_LOW_BATTERY -> "Batería baja"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_ALERTSTATECHANGED_STATE_TOO_MUCH_ANGLE -> "Ángulo excesivo"
                    else -> null
                }
                _uiState.update {
                    it.copy(flightSettings = it.flightSettings.copy(alertState = label))
                }
                if (label != null) {
                    flightLogRecorder.recordEvent(_uiState.value, "ALERT", label)
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOAUTORECORDCHANGED -> {
                val enabled = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOAUTORECORDCHANGED_ENABLED] as? Number)?.toInt()
                val storageId = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PICTURESETTINGSSTATE_VIDEOAUTORECORDCHANGED_MASS_STORAGE_ID] as? Number)?.toInt()
                if (enabled != null) {
                    _uiState.update {
                        it.copy(
                            cameraSettings = it.cameraSettings.copy(
                                videoAutorecordEnabled = enabled != 0,
                                videoAutorecordKnown = true,
                                videoAutorecordMassStorageId = storageId ?: it.cameraSettings.videoAutorecordMassStorageId,
                            )
                        )
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONREQUIREDSTATE -> {
                val requiredRaw = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONREQUIREDSTATE_REQUIRED] as? Number)?.toInt()
                if (requiredRaw != null) {
                    val isReq = requiredRaw != 0
                    val prevReq = _uiState.value.flightSettings.magnetoCalibration.required
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                magnetoCalibration = it.flightSettings.magnetoCalibration.copy(
                                    required = isReq,
                                    requiredKnown = true,
                                    statusNotice = if (isReq) {
                                        "Brújula requiere calibración en tierra"
                                    } else if (!it.flightSettings.magnetoCalibration.started) {
                                        it.flightSettings.magnetoCalibration.statusNotice
                                    } else {
                                        it.flightSettings.magnetoCalibration.statusNotice
                                    },
                                )
                            )
                        )
                    }
                    if (isReq && !prevReq) {
                        flightLogRecorder.recordEvent(_uiState.value, "MAGNETO", "Calibración requerida por el dron")
                    }
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTARTEDCHANGED -> {
                val startedRaw = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTARTEDCHANGED_STARTED] as? Number)?.toInt()
                if (startedRaw != null) {
                    val isStarted = startedRaw != 0
                    _uiState.update {
                        val prev = it.flightSettings.magnetoCalibration
                        val notice = when {
                            isStarted -> "Calibración iniciada: sigue la animación de la pantalla"
                            prev.xDone && prev.yDone && prev.zDone && !prev.failed -> "Calibración de brújula completada"
                            prev.failed -> "Calibración de brújula fallida; inténtalo en espacio abierto lejos de metales"
                            else -> "Calibración detenida"
                        }
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                magnetoCalibration = prev.copy(
                                    started = isStarted,
                                    currentAxis = if (isStarted) prev.currentAxis else MagnetoAxis.NONE,
                                    xDone = if (isStarted) false else prev.xDone,
                                    yDone = if (isStarted) false else prev.yDone,
                                    zDone = if (isStarted) false else prev.zDone,
                                    failed = if (isStarted) false else prev.failed,
                                    statusNotice = notice,
                                )
                            )
                        )
                    }
                    flightLogRecorder.recordEvent(
                        _uiState.value,
                        "MAGNETO",
                        if (isStarted) "Calibración iniciada" else "Calibración finalizada",
                    )
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED -> {
                val axisRaw = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS] as? Number)?.toInt()
                if (axisRaw != null) {
                    val axis = when (ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM.getFromValue(axisRaw)) {
                        ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM.ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_XAXIS -> MagnetoAxis.X_AXIS
                        ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM.ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_YAXIS -> MagnetoAxis.Y_AXIS
                        ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ENUM.ARCOMMANDS_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS_ZAXIS -> MagnetoAxis.Z_AXIS
                        else -> MagnetoAxis.NONE
                    }
                    _uiState.update {
                        it.copy(
                            flightSettings = it.flightSettings.copy(
                                magnetoCalibration = it.flightSettings.magnetoCalibration.copy(
                                    currentAxis = axis,
                                )
                            )
                        )
                    }
                    flightLogRecorder.recordEvent(_uiState.value, "MAGNETO_AXIS", axis.name)
                }
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED -> {
                val xDone = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_XAXISCALIBRATION] as? Number)?.toInt() == 1
                val yDone = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_YAXISCALIBRATION] as? Number)?.toInt() == 1
                val zDone = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_ZAXISCALIBRATION] as? Number)?.toInt() == 1
                val failed = (args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_CALIBRATIONFAILED] as? Number)?.toInt() == 1
                _uiState.update {
                    val prev = it.flightSettings.magnetoCalibration
                    it.copy(
                        flightSettings = it.flightSettings.copy(
                            magnetoCalibration = prev.copy(
                                xDone = if (failed) false else xDone,
                                yDone = if (failed) false else yDone,
                                zDone = if (failed) false else zDone,
                                failed = failed,
                                statusNotice = when {
                                    failed -> "Error calibrando brújula; mantén el dron alejado de concreto armado o metal"
                                    xDone && yDone && zDone -> "Ejes X, Y y Z calibrados correctamente"
                                    else -> prev.statusNotice
                                },
                            )
                        )
                    )
                }
                flightLogRecorder.recordEvent(
                    _uiState.value,
                    "MAGNETO_STATE",
                    "x=$xDone y=$yDone z=$zDone failed=$failed",
                )
            }

            ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED -> {
                val stateRaw = (args[ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE] as? Number)?.toInt()
                val label = when (stateRaw?.let { ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.getFromValue(it) }) {
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_AVAILABLE -> "Disponible"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_INPROGRESS -> "En curso"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_UNAVAILABLE -> "No disponible"
                    ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_NAVIGATEHOMESTATECHANGED_STATE_PENDING -> "Pendiente"
                    else -> null
                }
                _uiState.update {
                    it.copy(flightSettings = it.flightSettings.copy(navigateHomeState = label))
                }
            }

            else -> Unit
        }
        flightLogRecorder.maybeRecordTelemetry(_uiState.value)
    }

    private fun startRecordDurationTicker() {
        mainHandler.removeCallbacks(recordDurationTickRunnable)
        mainHandler.post(recordDurationTickRunnable)
    }

    private fun stopRecordDurationTicker() {
        mainHandler.removeCallbacks(recordDurationTickRunnable)
    }

    private fun connectToSkyControllerLocked(service: ARDiscoveryDeviceService) {
        if (released.get()) {
            Log.w(TAG, "Ignorando conexión a ${service.name}: SkyControllerConnectionManager liberado")
            return
        }
        val mux = UsbAccessoryMux.get(appContext).getMux()
        if (mux == null || !mux.isValid) {
            Log.w(TAG, "Servicio USB reportado pero MUX no está conectado todavía")
            return
        }

        val currentSession = activeSession
        if (currentSession != null && !currentSession.stopRequested.get() && currentSession.service == service) {
            return
        }

        disconnectControllerLocked("Reemplazando sesión anterior")

        var discoveryDevice: ARDiscoveryDevice? = null
        var createdSession: ManagedControllerSession? = null
        try {
            discoveryDevice = ARDiscoveryDevice(appContext, service)
            val controller = ARDeviceController(discoveryDevice)
            discoveryDevice.dispose()
            discoveryDevice = null

            val session = ManagedControllerSession(
                id = nextSessionId.incrementAndGet(),
                service = service,
                controller = controller,
            )
            createdSession = session
            RetiringControllerTracker.retain(session)

            val ctrlListener = createControllerListener(session)
            val strmListener = createStreamListener(session)
            session.controllerListener = ctrlListener
            session.streamListener = strmListener
            controller.addListener(ctrlListener)
            controller.addStreamListener(strmListener)

            activeSession = session
            Log.i(TAG, "[session=#${session.id}] Sesión creada para ${service.name}; solicitando start()")
            _uiState.update {
                it.copy(
                    skyControllerState = SkyControllerLinkState.CONNECTING,
                    skyControllerName = service.name,
                    errorMessage = null,
                )
            }

            session.lifecycle.set(ControllerLifecycleState.STARTING)
            val startError = controller.start()
            Log.i(TAG, "[session=#${session.id}] ARDeviceController.start() -> $startError")
            if (startError != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                if (activeSession === session) {
                    activeSession = null
                }
                RetiringControllerTracker.disposeNowIfSafe(session, "start() falló ($startError)")
                _uiState.update {
                    it.copy(
                        skyControllerState = SkyControllerLinkState.ERROR,
                        errorMessage = "ARDeviceController.start() falló: $startError",
                    )
                }
                return
            }

            session.everStartedSuccessfully.set(true)
            droneMediaRepository.attachDeviceService(service)
        } catch (t: Throwable) {
            Log.e(TAG, "Excepción al conectar con Skycontroller 2", t)
            discoveryDevice?.dispose()
            createdSession?.let { failedSession ->
                if (activeSession === failedSession) {
                    activeSession = null
                }
                if (failedSession.everStartedSuccessfully.get()) {
                    requestSessionStopLocked(failedSession, "Excepción posterior a start()")
                } else {
                    RetiringControllerTracker.disposeNowIfSafe(failedSession, "Excepción antes de start()")
                }
            }
            _uiState.update {
                it.copy(
                    skyControllerState = SkyControllerLinkState.ERROR,
                    errorMessage = "${t::class.java.simpleName}: ${t.message}",
                )
            }
        }
    }

    private fun startVideoStreamIfNeededLocked(session: ManagedControllerSession) {
        if (released.get() || activeSession !== session || session.stopRequested.get() || session.videoStreamStarted) {
            return
        }
        try {
            if (!session.controller.hasOutputVideoStream()) {
                Log.w(TAG, "[session=#${session.id}] El dispositivo no reporta output video stream")
                return
            }
            videoView?.onStreamStarting()
            val err = session.controller.startVideoStream()
            Log.i(TAG, "[session=#${session.id}] ARDeviceController.startVideoStream() -> $err")
            if (err == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                session.videoStreamStarted = true
            } else {
                _uiState.update {
                    it.copy(errorMessage = "startVideoStream() devolvió $err")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[session=#${session.id}] Error iniciando stream de video", t)
            _uiState.update {
                it.copy(errorMessage = "Error iniciando video: ${t.message}")
            }
        }
    }

    private fun restartVideoStreamLocked(session: ManagedControllerSession) {
        if (released.get() || activeSession !== session || session.stopRequested.get()) {
            return
        }
        val nativeState = runCatching { session.controller.state }.getOrNull()
        if (nativeState != ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING) {
            return
        }
        try {
            if (session.videoStreamStarted) {
                session.videoStreamStarted = false
                runCatching { session.controller.stopVideoStream() }
                Thread.sleep(STREAM_RESTART_GAP_MS)
            }
            if (released.get() || activeSession !== session || session.stopRequested.get()) {
                return
            }
            startVideoStreamIfNeededLocked(session)
        } catch (t: Throwable) {
            Log.w(TAG, "[session=#${session.id}] Error reiniciando stream de video", t)
        }
    }

    private fun reconnectUsbAndControllerLocked(reason: String, requestPermission: Boolean) {
        if (released.get()) return
        if (!usbRecoveryInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Reconexión USB ya en curso; omitiendo duplicado ($reason)")
            return
        }
        try {
            Log.i(TAG, "Iniciando reconexión USB/MUX + controlador ($reason)")
            _uiState.update { it.copy(errorMessage = null) }

            disconnectControllerLocked(reason)
            if (released.get()) return

            val mux = UsbAccessoryMux.get(appContext)
            mux.forceReopenMux(requestPermission)

            mainHandler.post {
                if (released.get()) return@post
                ensureDiscoveryInfrastructureStarted()
                discoveryDelegate.onServicesDevicesListUpdated()
            }

            // Respaldo por si ARDiscoveryUsbDiscovery tardó en difundir el servicio
            val token = recoveryToken.get()
            mainHandler.postDelayed({
                if (!released.get() && isAppInForeground && recoveryToken.get() == token &&
                    activeSession == null && mux.isHealthy()
                ) {
                    Log.d(TAG, "Reintentando lectura de ARDiscoveryService tras reapertura MUX")
                    ensureDiscoveryInfrastructureStarted()
                    discoveryDelegate.onServicesDevicesListUpdated()
                }
            }, POST_REOPEN_DISCOVERY_RETRY_MS)
        } finally {
            usbRecoveryInFlight.set(false)
        }
    }

    private fun disconnectControllerLocked(reason: String) {
        val session = activeSession
        if (session == null) {
            Log.d(TAG, "disconnectControllerLocked($reason): sin sesión activa")
            return
        }
        requestSessionStopLocked(session, reason)
    }

    private fun requestSessionStopLocked(session: ManagedControllerSession, reason: String) {
        if (!session.stopRequested.compareAndSet(false, true)) {
            Log.d(TAG, "[session=#${session.id}] Intento duplicado de parada ignorado ($reason)")
            return
        }

        Log.i(TAG, "[session=#${session.id}] Solicitud de parada motivo=$reason (estado=${session.lifecycle.get()})")
        RetiringControllerTracker.retain(session)
        if (activeSession === session) {
            activeSession = null
        }

        droneMediaRepository.attachDeviceService(null)
        autoExposureController.updateStreamReceiving(false)
        stopRecordDurationTicker()
        videoView?.onStreamStopped()

        try {
            val nativeState = runCatching { session.controller.state }.getOrNull()
            Log.i(TAG, "[session=#${session.id}] Estado nativo actual al iniciar parada: $nativeState")

            when (nativeState) {
                ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED -> {
                    session.lifecycle.set(ControllerLifecycleState.STOPPED)
                    session.stopSignal.countDown()
                    RetiringControllerTracker.scheduleDispose(session, "Ya estaba en STOPPED ($reason)")
                    return
                }

                ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPING -> {
                    session.lifecycle.set(ControllerLifecycleState.STOPPING)
                    Log.i(TAG, "[session=#${session.id}] Controlador ya en STOPPING; sin repetir stop(), esperando confirmación STOPPED")
                }

                ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING,
                ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STARTING -> {
                    session.lifecycle.set(ControllerLifecycleState.STOPPING)
                    if (session.videoStreamStarted || nativeState == ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING) {
                        session.videoStreamStarted = false
                        runCatching { session.controller.stopVideoStream() }
                    }
                    val stopErr = session.controller.stop()
                    Log.i(TAG, "[session=#${session.id}] controller.stop() -> $stopErr")
                    if (stopErr != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                        // Por seguridad nunca llamamos dispose() si StartRun/StopRun pudo quedar activo
                        Log.w(TAG, "[session=#${session.id}] controller.stop() devolvió $stopErr; sesión retenida esperando STOPPED")
                    }
                }

                else -> {
                    if (!session.everStartedSuccessfully.get()) {
                        RetiringControllerTracker.scheduleDispose(session, "Nunca inició ($reason)")
                        return
                    }
                    session.lifecycle.set(ControllerLifecycleState.STOPPING)
                }
            }

            // Espera acotada solo para serializar desconexión limpia; NUNCA autoriza dispose() si vence
            val stoppedInTime = session.stopSignal.await(STOP_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (stoppedInTime) {
                Log.i(TAG, "[session=#${session.id}] Parada completada dentro de ventana (${STOP_WAIT_TIMEOUT_MS}ms)")
                RetiringControllerTracker.scheduleDispose(session, "STOPPED confirmado ($reason)")
            } else {
                Log.w(
                    TAG,
                    "[session=#${session.id}] Timeout (${STOP_WAIT_TIMEOUT_MS}ms) esperando STOPPED; sesión retenida (pendientes=${RetiringControllerTracker.pendingCount()}) sin llamar dispose()",
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[session=#${session.id}] Error durante solicitud de parada; sesión retenida esperando STOPPED", t)
        }
    }

    fun release() {
        released.set(true)
        if (!started.compareAndSet(true, false)) {
            sdkExecutor.execute {
                disconnectControllerLocked("release()")
            }
            return
        }
        Log.i(TAG, "Liberando SkyControllerConnectionManager (sesiones pendientes en RetiringControllerTracker=${RetiringControllerTracker.pendingCount()})")
        mainHandler.removeCallbacksAndMessages(null)
        autoExposureController.release()
        localMp4Recorder.release()
        droneMediaRepository.release()
        flightLogRecorder.release()
        attachVideoView(null)

        if (discoveryReceiverRegistered) {
            discoveryReceiver?.let { receiver ->
                runCatching { LocalBroadcastManager.getInstance(appContext).unregisterReceiver(receiver) }
            }
            discoveryReceiverRegistered = false
        }

        discoveryService?.let { service ->
            runCatching { service.stopUsbDiscovering() }
        }
        discoveryService = null

        if (serviceBound) {
            runCatching { appContext.unbindService(serviceConnection) }
            serviceBound = false
        }

        if (_uiState.value.nativeLoadStatus is NativeLoadStatus.Loaded) {
            runCatching { UsbAccessoryMux.get(appContext).setMuxStateListener(null) }
        }

        // No apagar sdkExecutor ni RetiringControllerTracker.disposeExecutor para permitir limpieza diferida tras STOPPED
        sdkExecutor.execute {
            disconnectControllerLocked("release()")
        }
    }

    private fun UsbAccessory.toSummary(): UsbAccessorySummary {
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        return UsbAccessorySummary(
            manufacturer = manufacturer,
            model = model,
            version = version,
            serial = serial,
            hasPermission = usbManager?.hasPermission(this) ?: false,
        )
    }

    companion object {
        private const val TAG = "SkyControllerConnMgr"
        private const val RECORD_CMD_TIMEOUT_MS = 4500L
        private const val STOP_WAIT_TIMEOUT_MS = 2500L
        private const val RESUME_STREAM_CHECK_DELAY_MS = 650L
        private const val RESUME_ESCALATION_DELAY_MS = 3000L
        private const val STREAM_RESTART_GAP_MS = 150L
        private const val POST_REOPEN_DISCOVERY_RETRY_MS = 850L
        private val nextSessionId = AtomicLong(0L)
    }
}
