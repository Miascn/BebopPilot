package com.beboppilot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.beboppilot.app.drone.SkyControllerConnectionManager
import com.beboppilot.app.sdk.BebopPilotUiState
import com.beboppilot.app.sdk.DroneMediaItem
import com.beboppilot.app.ui.ActiveSidePanel
import com.beboppilot.app.ui.AnimatedCameraSidePanel
import com.beboppilot.app.ui.BottomCameraDock
import com.beboppilot.app.ui.CameraSettingsActions
import com.beboppilot.app.ui.CompactTopHudBar
import com.beboppilot.app.ui.ConnectionStatusOverlay
import com.beboppilot.app.ui.FlightSettingsActions
import com.beboppilot.app.ui.FloatingAlertsStrip
import com.beboppilot.app.ui.LowBatteryAlarmBanner
import com.beboppilot.app.video.BebopH264SurfaceView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    private lateinit var connectionManager: SkyControllerConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveCameraMode()

        connectionManager = SkyControllerConnectionManager(applicationContext)
        connectionManager.start()

        setContent {
            val uiState by connectionManager.uiState.collectAsState()

            LaunchedEffect(uiState.cameraSettings.localPreviewBrightness) {
                val lp = window.attributes
                lp.screenBrightness = uiState.cameraSettings.localPreviewBrightness
                    ?.coerceIn(0.15f, 1.0f)
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    background = Color.Black,
                    surface = Color(0xFF10151D),
                ),
            ) {
                val flightActions = remember(connectionManager) {
                    FlightSettingsActions(
                        onApplyPreset = connectionManager::applyFlightPreset,
                        onSetMaxRotationSpeed = connectionManager::setMaxRotationSpeed,
                        onSetMaxTilt = connectionManager::setMaxTilt,
                        onSetMaxPitchRollRate = connectionManager::setMaxPitchRollRotationSpeed,
                        onSetMaxVerticalSpeed = connectionManager::setMaxVerticalSpeed,
                        onSetMaxAltitude = connectionManager::setMaxAltitude,
                        onSetMaxDistance = connectionManager::setMaxDistance,
                        onSetGeofence = connectionManager::setGeofenceEnabled,
                        onSetBankedTurn = connectionManager::setBankedTurnEnabled,
                        onSetHullProtection = connectionManager::setHullProtectionPresent,
                        onSetReturnHomeType = connectionManager::setReturnHomeType,
                        onSetReturnHomeDelay = connectionManager::setReturnHomeDelay,
                        onSetControllerExpo = connectionManager::setControllerStickExpo,
                        onFlatTrim = connectionManager::sendFlatTrim,
                        onStartMagnetoCalibration = connectionManager::startMagnetoCalibration,
                        onAbortMagnetoCalibration = connectionManager::abortMagnetoCalibration,
                    )
                }
                val cameraActions = remember(connectionManager) {
                    CameraSettingsActions(
                        onSetResolution = connectionManager::setVideoResolutionProfile,
                        onSetFramerate = connectionManager::setVideoFramerate,
                        onSetRecordingMode = connectionManager::setVideoRecordingMode,
                        onSetStreamMode = connectionManager::setVideoStreamMode,
                        onSetStabilization = connectionManager::setVideoStabilizationMode,
                        onSetVideoAutorecord = connectionManager::setVideoAutorecordEnabled,
                        onSetSaturation = connectionManager::setSaturation,
                        onSetAutoExposure = connectionManager::setAutoExposureEnabled,
                        onSetManualEv = connectionManager::setManualExposureEv,
                        onSetWhiteBalance = connectionManager::setWhiteBalanceMode,
                        onSetLocalBrightness = connectionManager::setLocalPreviewBrightness,
                    )
                }
                BebopCameraScreen(
                    uiState = uiState,
                    flightActions = flightActions,
                    cameraActions = cameraActions,
                    onAttachVideoView = connectionManager::attachVideoView,
                    onRefreshUsb = connectionManager::refreshUsbAndMux,
                    onRetryPermission = connectionManager::retryUsbPermission,
                    onToggleDroneRecording = connectionManager::toggleDroneRecording,
                    onCapturePhoto = connectionManager::captureDronePhoto,
                    onToggleLocalRecording = connectionManager::toggleLocalRecording,
                    onRefreshGallery = connectionManager::refreshDroneGallery,
                    onRefreshLocalGallery = connectionManager::refreshLocalGallery,
                    onDownloadMedia = connectionManager::downloadDroneMedia,
                    onDeleteDroneMedia = connectionManager::deleteDroneMedia,
                    onCancelDownload = connectionManager::cancelDroneMediaTransfer,
                    onExportFlightLog = connectionManager::exportLatestFlightLog,
                    onDismissDroneNotice = connectionManager::clearDroneRecordingNotice,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        connectionManager.refreshUsbAndMux()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveCameraMode()
        connectionManager.onAppResumed()
    }

    override fun onPause() {
        connectionManager.onAppPaused()
        super.onPause()
    }

    override fun onDestroy() {
        connectionManager.release()
        super.onDestroy()
    }

    private fun enableImmersiveCameraMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun BebopCameraScreen(
    uiState: BebopPilotUiState,
    flightActions: FlightSettingsActions,
    cameraActions: CameraSettingsActions,
    onAttachVideoView: (BebopH264SurfaceView?) -> Unit,
    onRefreshUsb: () -> Unit,
    onRetryPermission: () -> Unit,
    onToggleDroneRecording: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleLocalRecording: () -> Unit,
    onRefreshGallery: () -> Unit,
    onRefreshLocalGallery: () -> Unit,
    onDownloadMedia: (DroneMediaItem) -> Unit,
    onDeleteDroneMedia: (DroneMediaItem) -> Unit,
    onCancelDownload: () -> Unit,
    onExportFlightLog: () -> Unit,
    onDismissDroneNotice: () -> Unit,
) {
    val context = LocalContext.current
    var activePanel by remember { mutableStateOf(ActiveSidePanel.NONE) }
    var lastSelectedTab by remember { mutableStateOf(ActiveSidePanel.FLIGHT_SETTINGS) }
    var hudVisible by remember { mutableStateOf(true) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingReadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val droneBatt = uiState.bebop2BatteryPercent
    val isLowBatteryAlarm = droneBatt != null && droneBatt in 1..15
    var lowBatteryAlarmMuted by remember { mutableStateOf(false) }

    LaunchedEffect(droneBatt) {
        if (droneBatt == null || droneBatt > 15) {
            lowBatteryAlarmMuted = false
        }
    }

    LaunchedEffect(isLowBatteryAlarm, lowBatteryAlarmMuted) {
        if (!isLowBatteryAlarm || lowBatteryAlarmMuted) return@LaunchedEffect
        val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull()
            ?: return@LaunchedEffect
        try {
            while (isActive) {
                runCatching { toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350) }
                delay(500L)
                runCatching { toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350) }
                delay(5_500L)
            }
        } finally {
            runCatching { toneGen.release() }
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) {
            action?.invoke()
        }
    }

    fun runWithMediaStoreWriteAccess(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingStorageAction = action
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val readMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val action = pendingReadAction
        pendingReadAction = null
        action?.invoke()
    }

    fun runWithMediaReadAccess(action: () -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = required.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingReadAction = action
            readMediaPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    val surfaceView = remember(context) { BebopH264SurfaceView(context) }
    DisposableEffect(surfaceView) {
        onAttachVideoView(surfaceView)
        onDispose {
            onAttachVideoView(null)
        }
    }

    val metrics = uiState.videoMetrics
    val videoAspect = metrics.effectiveAspectRatio ?: (16f / 9f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Capa de video persistente ocupando la mayor parte de la pantalla sin deformación
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (activePanel != ActiveSidePanel.NONE) {
                        activePanel = ActiveSidePanel.NONE
                    } else {
                        hudVisible = !hudVisible
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight
            val containerAspect = if (containerHeight.value > 0f) {
                containerWidth.value / containerHeight.value
            } else {
                videoAspect
            }
            val (fittedWidth, fittedHeight) = if (containerAspect > videoAspect) {
                (containerHeight * videoAspect) to containerHeight
            } else {
                containerWidth to (containerWidth / videoAspect)
            }

            AndroidView(
                factory = { surfaceView },
                modifier = Modifier.size(width = fittedWidth, height = fittedHeight),
            )
        }

        val isSettingsOpen = activePanel != ActiveSidePanel.NONE

        // Indicador de conexión discreto cuando el video todavía no está recibiendo frames
        if (!isSettingsOpen) {
            ConnectionStatusOverlay(
                uiState = uiState,
                onRefreshUsb = onRefreshUsb,
                onRetryPermission = onRetryPermission,
                modifier = Modifier
                    .align(Alignment.Center)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp),
            )
        }

        // Panel de configuración en pantalla completa modo cristalino sobre el video en vivo
        AnimatedCameraSidePanel(
            activePanel = activePanel,
            uiState = uiState,
            flightActions = flightActions,
            cameraActions = cameraActions,
            onSelectTab = { tab ->
                lastSelectedTab = tab
                if (tab == ActiveSidePanel.GALLERY &&
                    uiState.bebop2State == com.beboppilot.app.sdk.Bebop2LinkState.CONNECTED &&
                    uiState.gallery.items.isEmpty()
                ) {
                    onRefreshGallery()
                }
                activePanel = tab
            },
            onClose = { activePanel = ActiveSidePanel.NONE },
            onRefreshGallery = onRefreshGallery,
            onRefreshLocalGallery = {
                runWithMediaReadAccess { onRefreshLocalGallery() }
            },
            onDownloadMedia = { item ->
                runWithMediaStoreWriteAccess { onDownloadMedia(item) }
            },
            onDeleteDroneMedia = onDeleteDroneMedia,
            onCancelDownload = onCancelDownload,
            onExportFlightLog = {
                runWithMediaStoreWriteAccess { onExportFlightLog() }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Capa HUD inmersiva (barra superior, avisos y dock inferior)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            AnimatedVisibility(
                visible = hudVisible && !isSettingsOpen,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                CompactTopHudBar(uiState = uiState)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (droneBatt != null && droneBatt in 1..15) {
                    LowBatteryAlarmBanner(
                        batteryPercent = droneBatt,
                        muted = lowBatteryAlarmMuted,
                        onToggleMute = { lowBatteryAlarmMuted = !lowBatteryAlarmMuted },
                    )
                }
                if (!isSettingsOpen) {
                    FloatingAlertsStrip(
                        uiState = uiState,
                        onDismissDroneNotice = onDismissDroneNotice,
                    )
                }
            }

            AnimatedVisibility(
                visible = hudVisible && !isSettingsOpen,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomCameraDock(
                    uiState = uiState,
                    activePanel = activePanel,
                    onCapturePhoto = onCapturePhoto,
                    onToggleDroneRecording = onToggleDroneRecording,
                    onToggleLocalRecording = {
                        runWithMediaStoreWriteAccess(onToggleLocalRecording)
                    },
                    onToggleSettings = {
                        if (activePanel != ActiveSidePanel.NONE) {
                            activePanel = ActiveSidePanel.NONE
                        } else {
                            if (lastSelectedTab == ActiveSidePanel.GALLERY &&
                                uiState.bebop2State == com.beboppilot.app.sdk.Bebop2LinkState.CONNECTED &&
                                uiState.gallery.items.isEmpty()
                            ) {
                                onRefreshGallery()
                            }
                            activePanel = lastSelectedTab
                        }
                    },
                )
            }
        }
    }
}
