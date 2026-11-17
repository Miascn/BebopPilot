package com.beboppilot.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import com.beboppilot.app.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beboppilot.app.sdk.Bebop2LinkState
import com.beboppilot.app.sdk.BebopPilotUiState
import com.beboppilot.app.sdk.BoundedFloatSetting
import com.beboppilot.app.sdk.DroneMediaItem
import com.beboppilot.app.sdk.DroneRecordState
import com.beboppilot.app.sdk.FlightPreset
import com.beboppilot.app.sdk.LocalMediaItem
import com.beboppilot.app.sdk.LocalRecordState
import com.beboppilot.app.sdk.MagnetoAxis
import com.beboppilot.app.sdk.ReturnHomeType
import com.beboppilot.app.sdk.SkyControllerLinkState
import com.beboppilot.app.sdk.StickExpoMode
import com.beboppilot.app.sdk.VideoFramerateMode
import com.beboppilot.app.sdk.VideoRecordingQualityMode
import com.beboppilot.app.sdk.VideoResolutionProfile
import com.beboppilot.app.sdk.VideoStabilizationMode
import com.beboppilot.app.sdk.VideoStreamReliabilityMode
import com.beboppilot.app.sdk.WhiteBalanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class CameraSettingsActions(
    val onSetResolution: (VideoResolutionProfile) -> Unit,
    val onSetFramerate: (VideoFramerateMode) -> Unit,
    val onSetRecordingMode: (VideoRecordingQualityMode) -> Unit,
    val onSetStreamMode: (VideoStreamReliabilityMode) -> Unit,
    val onSetStabilization: (VideoStabilizationMode) -> Unit,
    val onSetVideoAutorecord: (Boolean) -> Unit,
    val onSetSaturation: (Float) -> Unit,
    val onSetAutoExposure: (Boolean) -> Unit,
    val onSetManualEv: (Float) -> Unit,
    val onSetWhiteBalance: (WhiteBalanceMode) -> Unit,
    val onSetLocalBrightness: (Float?) -> Unit,
)

data class FlightSettingsActions(
    val onApplyPreset: (FlightPreset) -> Unit,
    val onSetMaxRotationSpeed: (Float) -> Unit,
    val onSetMaxTilt: (Float) -> Unit,
    val onSetMaxPitchRollRate: (Float) -> Unit,
    val onSetMaxVerticalSpeed: (Float) -> Unit,
    val onSetMaxAltitude: (Float) -> Unit,
    val onSetMaxDistance: (Float) -> Unit,
    val onSetGeofence: (Boolean) -> Unit,
    val onSetBankedTurn: (Boolean) -> Unit,
    val onSetHullProtection: (Boolean) -> Unit,
    val onSetReturnHomeType: (ReturnHomeType) -> Unit,
    val onSetReturnHomeDelay: (Int) -> Unit,
    val onSetControllerExpo: (axisIdOrAll: Int, expo: StickExpoMode) -> Unit,
    val onFlatTrim: () -> Unit,
    val onStartMagnetoCalibration: () -> Unit,
    val onAbortMagnetoCalibration: () -> Unit,
)

@Composable
fun AnimatedCameraSidePanel(
    activePanel: ActiveSidePanel,
    uiState: BebopPilotUiState,
    flightActions: FlightSettingsActions,
    cameraActions: CameraSettingsActions,
    onSelectTab: (ActiveSidePanel) -> Unit,
    onClose: () -> Unit,
    onRefreshGallery: () -> Unit,
    onRefreshLocalGallery: () -> Unit,
    onDownloadMedia: (DroneMediaItem) -> Unit,
    onDeleteDroneMedia: (DroneMediaItem) -> Unit,
    onCancelDownload: () -> Unit,
    onExportFlightLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = activePanel != ActiveSidePanel.NONE,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize(),
    ) {
        Surface(
            color = HudColors.PanelBg,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bebop_pilot_logo),
                            contentDescription = "Logo de BebopPilot",
                            modifier = Modifier
                                .width(34.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column {
                            Text(
                                text = "BebopPilot · Configuración",
                                color = HudColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = uiState.bebop2Name?.let { "$it · Cámara activa al fondo · por MiasCN" }
                                    ?: "Ajustes en tiempo real · por MiasCN",
                                color = HudColors.TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BatteryStatusBadge(percent = uiState.bebop2BatteryPercent)
                        Surface(
                            color = Color(0x6E1E293B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(14.dp))
                                .clickable(onClick = onClose),
                        ) {
                            Text(
                                text = "Cerrar ✕",
                                color = HudColors.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val tabs = listOf(
                        ActiveSidePanel.FLIGHT_SETTINGS to "Vuelo",
                        ActiveSidePanel.CAMERA_SETTINGS to "Cámara",
                        ActiveSidePanel.TELEMETRY to "Estado",
                        ActiveSidePanel.GALLERY to "Galería",
                        ActiveSidePanel.ABOUT to "Acerca de",
                    )
                    tabs.forEach { (tab, label) ->
                        val selected = activePanel == tab
                        Surface(
                            color = if (selected) HudColors.Accent.copy(alpha = 0.28f) else Color(0x520F172A),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) HudColors.Accent else HudColors.GlassBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelectTab(tab) },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) HudColors.Accent else HudColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = HudColors.GlassBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activePanel) {
                        ActiveSidePanel.FLIGHT_SETTINGS -> FlightSettingsContent(
                            uiState = uiState,
                            actions = flightActions,
                        )
                        ActiveSidePanel.CAMERA_SETTINGS -> CameraSettingsContent(
                            uiState = uiState,
                            actions = cameraActions,
                        )
                        ActiveSidePanel.TELEMETRY -> TelemetryContent(
                            uiState = uiState,
                            onExportFlightLog = onExportFlightLog,
                        )
                        ActiveSidePanel.GALLERY -> GalleryContent(
                            uiState = uiState,
                            onRefreshGallery = onRefreshGallery,
                            onRefreshLocalGallery = onRefreshLocalGallery,
                            onDownloadMedia = onDownloadMedia,
                            onDeleteDroneMedia = onDeleteDroneMedia,
                            onCancelDownload = onCancelDownload,
                        )
                        ActiveSidePanel.ABOUT -> AboutContent(uiState = uiState)
                        ActiveSidePanel.NONE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveTwoColumnScroll(
    leftColumn: @Composable () -> Unit,
    rightColumn: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 620.dp
        val scrollState = rememberScrollState()
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    leftColumn()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rightColumn()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leftColumn()
                rightColumn()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlightSettingsContent(
    uiState: BebopPilotUiState,
    actions: FlightSettingsActions,
) {
    val fs = uiState.flightSettings
    val expo = uiState.controllerExpo
    val magneto = fs.magnetoCalibration
    val droneConnected = uiState.bebop2State == Bebop2LinkState.CONNECTED
    val sc2Connected = uiState.skyControllerState == SkyControllerLinkState.CONNECTED
    val isLanded = uiState.bebop2FlyingState == null ||
        uiState.bebop2FlyingState == "ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED"

    ResponsiveTwoColumnScroll(
        leftColumn = {
            if (!droneConnected) {
                Text(
                    text = "Conecta el Bebop 2 para leer y aplicar límites de velocidad, giro y calibración.",
                    color = HudColors.WarnAmber,
                    fontSize = 11.sp,
                )
            }

            PanelSectionCard(title = "Perfiles de vuelo (FreeFlight Pro)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlightPreset.entries.forEach { preset ->
                        OutlinedButton(
                            onClick = { actions.onApplyPreset(preset) },
                            enabled = droneConnected,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(preset.label, color = HudColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(preset.description, color = HudColors.TextSecondary, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            PanelSectionCard(title = "Giro (Eje Y / Yaw) y Agilidad") {
                BoundedSliderRow(
                    title = "Velocidad de giro Yaw (Eje Y)",
                    subtitle = "Qué tan rápido rota el dron sobre su propio eje",
                    setting = fs.maxRotationSpeed,
                    unit = "°/s",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxRotationSpeed,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                BoundedSliderRow(
                    title = "Inclinación máxima (Pitch/Roll)",
                    subtitle = "Controla la velocidad horizontal máxima",
                    setting = fs.maxTilt,
                    unit = "°",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxTilt,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                BoundedSliderRow(
                    title = "Respuesta Pitch/Roll",
                    subtitle = "Rapidez con que alcanza la inclinación",
                    setting = fs.maxPitchRollRotationSpeed,
                    unit = "°/s",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxPitchRollRate,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                BoundedSliderRow(
                    title = "Velocidad vertical máxima",
                    subtitle = "Ascenso y descenso",
                    setting = fs.maxVerticalSpeed,
                    unit = "m/s",
                    decimals = 1,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxVerticalSpeed,
                )
            }

            PanelSectionCard(title = "Sensibilidad Joysticks (Skycontroller 2)") {
                Text(
                    text = "Lineal da giro inmediato desde el centro; Expo alta suaviza el centro del stick.",
                    color = HudColors.TextSecondary,
                    fontSize = 11.sp,
                )
                val targetAxis = expo.yawAxisId ?: -1
                Text(
                    text = if (expo.yawAxisId != null) {
                        "Curva eje Yaw (#${expo.yawAxisId}): ${expo.yawExpo.label}"
                    } else {
                        "Curva general de sticks (todos los ejes)"
                    },
                    color = HudColors.TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                val selectableExpos = listOf(
                    StickExpoMode.LINEAR,
                    StickExpoMode.EXPO_0,
                    StickExpoMode.EXPO_1,
                    StickExpoMode.EXPO_2,
                    StickExpoMode.EXPO_4,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectableExpos.forEach { mode ->
                        val selected = expo.yawExpo == mode
                        Surface(
                            color = if (selected) HudColors.Accent.copy(alpha = 0.25f) else Color(0x520F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (selected) HudColors.Accent else HudColors.GlassBorder,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = sc2Connected) {
                                    actions.onSetControllerExpo(targetAxis, mode)
                                },
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) HudColors.Accent else HudColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                if (expo.yawAxisId != null) {
                    OutlinedButton(
                        onClick = { actions.onSetControllerExpo(-1, StickExpoMode.LINEAR) },
                        enabled = sc2Connected,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Poner todos los ejes en Lineal", color = HudColors.Accent, fontSize = 11.sp)
                    }
                }
            }
        },
        rightColumn = {
            PanelSectionCard(title = "Calibración de Brújula y Nivelado") {
                val calibColor = when {
                    magneto.started -> HudColors.Accent
                    magneto.failed -> HudColors.RecRed
                    magneto.required -> HudColors.WarnAmber
                    magneto.requiredKnown -> HudColors.OkGreen
                    else -> HudColors.TextSecondary
                }
                val calibLabel = when {
                    magneto.started -> "Calibrando brújula en vivo..."
                    magneto.failed -> "Calibración fallida"
                    magneto.required -> "Calibración requerida (evita tambaleo GPS)"
                    magneto.requiredKnown -> "Brújula calibrada (OK)"
                    else -> "Guía visual y calibración de brújula"
                }
                Text(
                    text = calibLabel,
                    color = calibColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )

                var previewAxis by remember { mutableStateOf(MagnetoAxis.X_AXIS) }
                val displayedAxis = when {
                    magneto.started && magneto.currentAxis != MagnetoAxis.NONE -> magneto.currentAxis
                    else -> previewAxis
                }

                // Selector interactivo en reposo / Indicador en vivo durante calibración
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val axes = listOf(
                        Triple(MagnetoAxis.X_AXIS, "X (Roll)", magneto.xDone),
                        Triple(MagnetoAxis.Y_AXIS, "Y (Pitch)", magneto.yDone),
                        Triple(MagnetoAxis.Z_AXIS, "Z (Yaw)", magneto.zDone),
                    )
                    axes.forEach { (axisEnum, name, done) ->
                        val active = displayedAxis == axisEnum
                        val chipColor = when {
                            done -> HudColors.OkGreen
                            active -> HudColors.Accent
                            else -> HudColors.TextSecondary
                        }
                        Surface(
                            color = if (active) chipColor.copy(alpha = 0.24f) else Color(0x520F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, chipColor, RoundedCornerShape(8.dp))
                                .clickable(enabled = !magneto.started) {
                                    previewAxis = axisEnum
                                },
                        ) {
                            Text(
                                text = if (done) "$name ✓" else name,
                                color = chipColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                MagnetoCalibrationGuideBox(
                    axis = displayedAxis,
                    isLive = magneto.started,
                )

                magneto.statusNotice?.let { notice ->
                    Text(
                        text = notice,
                        color = if (magneto.failed && !magneto.started) HudColors.RecRed else HudColors.WarnAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (magneto.started) {
                        OutlinedButton(
                            onClick = actions.onAbortMagnetoCalibration,
                            enabled = droneConnected,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancelar calibración", color = HudColors.WarnAmber, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = actions.onStartMagnetoCalibration,
                            enabled = droneConnected && isLanded,
                            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Calibrar brújula", color = Color(0xFF082F49), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = actions.onFlatTrim,
                        enabled = droneConnected && isLanded,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Nivelar Flat Trim", color = HudColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Exterior abierto (>3–5 m de puertas/rejas de metal). Toca X, Y o Z arriba para ver la animación antes de empezar.",
                    color = HudColors.TextSecondary,
                    fontSize = 10.sp,
                )
            }

            PanelSectionCard(title = "Modo de Vuelo y Seguridad") {
                LabeledSwitchRow(
                    title = "Giro coordinado (Banked Turn)",
                    subtitle = if (fs.bankedTurnKnown) "Curvas suaves estilo avión" else "Estado pendiente del dron",
                    checked = fs.bankedTurnEnabled,
                    enabled = droneConnected,
                    onCheckedChange = actions.onSetBankedTurn,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                LabeledSwitchRow(
                    title = "Protectores de hélices",
                    subtitle = if (fs.hullProtectionKnown) "Ajusta estabilidad con carenado" else "Estado pendiente del dron",
                    checked = fs.hullProtectionPresent,
                    enabled = droneConnected,
                    onCheckedChange = actions.onSetHullProtection,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                BoundedSliderRow(
                    title = "Altura máxima",
                    subtitle = "Techo vertical sobre el despegue",
                    setting = fs.maxAltitude,
                    unit = "m",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxAltitude,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                BoundedSliderRow(
                    title = "Distancia máxima",
                    subtitle = "Radio límite horizontal",
                    setting = fs.maxDistance,
                    unit = "m",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetMaxDistance,
                )
                LabeledSwitchRow(
                    title = "Activar geocerca (Geofence)",
                    subtitle = if (fs.geofenceKnown) "Impide rebasar distancia máxima" else "Estado pendiente del dron",
                    checked = fs.geofenceEnabled,
                    enabled = droneConnected,
                    onCheckedChange = actions.onSetGeofence,
                )
            }

            PanelSectionCard(title = "Retorno a Casa (RTH)") {
                Text(
                    text = "Punto de retorno (Home)",
                    color = HudColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(ReturnHomeType.TAKEOFF, ReturnHomeType.PILOT, ReturnHomeType.FOLLOWEE).forEach { homeType ->
                        val selected = fs.returnHomeType == homeType
                        Surface(
                            color = if (selected) HudColors.Accent.copy(alpha = 0.25f) else Color(0x520F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (selected) HudColors.Accent else HudColors.GlassBorder,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = droneConnected) { actions.onSetReturnHomeType(homeType) },
                        ) {
                            Text(
                                text = homeType.label,
                                color = if (selected) HudColors.Accent else HudColors.TextPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                val delaySetting = BoundedFloatSetting(
                    current = fs.returnHomeDelaySec.toFloat(),
                    min = 0f,
                    max = 120f,
                    known = fs.returnHomeDelayKnown,
                )
                BoundedSliderRow(
                    title = "Retardo RTH tras desconexión",
                    subtitle = "Segundos de espera antes de regresar",
                    setting = delaySetting,
                    unit = "s",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = { actions.onSetReturnHomeDelay(it.roundToInt()) },
                )
            }
        },
    )
}

@Composable
private fun BoundedSliderRow(
    title: String,
    subtitle: String,
    setting: BoundedFloatSetting,
    unit: String,
    decimals: Int,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    val safeMin = setting.min
    val safeMax = if (setting.max > safeMin) setting.max else safeMin + 1f
    val clamped = setting.current.coerceIn(safeMin, safeMax)
    var draftValue by remember(clamped, setting.known) { mutableFloatStateOf(clamped) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HudColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = HudColors.TextSecondary, fontSize = 10.sp)
            }
            Text(
                text = if (setting.known) {
                    String.format(Locale.US, "%.${decimals}f %s", draftValue, unit)
                } else {
                    "N/D"
                },
                color = HudColors.Accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = draftValue.coerceIn(safeMin, safeMax),
            onValueChange = { draftValue = it },
            onValueChangeFinished = { onCommit(draftValue) },
            valueRange = safeMin..safeMax,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = HudColors.Accent,
                activeTrackColor = HudColors.Accent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = String.format(Locale.US, "Min %.${decimals}f %s", safeMin, unit),
                color = HudColors.TextSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = String.format(Locale.US, "Max %.${decimals}f %s", safeMax, unit),
                color = HudColors.TextSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = HudColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HudColors.TextSecondary, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF082F49),
                checkedTrackColor = HudColors.Accent,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraSettingsContent(
    uiState: BebopPilotUiState,
    actions: CameraSettingsActions,
) {
    val settings = uiState.cameraSettings
    val droneConnected = uiState.bebop2State == Bebop2LinkState.CONNECTED
    val fw = uiState.bebop2Firmware
    val isAnyRecording = uiState.droneRecording.state == DroneRecordState.RECORDING ||
        uiState.droneRecording.state == DroneRecordState.STARTING ||
        uiState.localRecording.state == LocalRecordState.RECORDING ||
        uiState.localRecording.state == LocalRecordState.WAITING_KEYFRAME ||
        uiState.localRecording.state == LocalRecordState.WAITING_CONFIG
    val canChangeRes = droneConnected && !isAnyRecording && (!fw.isKnown || fw.supportsVideoResolutionControls)

    val safeMin = settings.exposureMin
    val safeMax = if (settings.exposureMax > safeMin) settings.exposureMax else safeMin + 1f
    val clampedEv = settings.exposureValue.coerceIn(safeMin, safeMax)

    ResponsiveTwoColumnScroll(
        leftColumn = {
            PanelSectionCard(title = "Resolución y Calidad de Vídeo") {
                if (!droneConnected) {
                    Text(
                        text = "Conecta el Bebop 2 para configurar resolución, FPS y calidad.",
                        color = HudColors.WarnAmber,
                        fontSize = 11.sp,
                    )
                } else if (fw.isKnown && !fw.supportsVideoResolutionControls) {
                    Text(
                        text = "Tu firmware (${fw.softwareVersion}) es < 3.4.0 y no soporta cambio de resolución.",
                        color = HudColors.WarnAmber,
                        fontSize = 11.sp,
                    )
                } else if (isAnyRecording) {
                    Text(
                        text = "Detén la grabación actual para cambiar la resolución.",
                        color = HudColors.WarnAmber,
                        fontSize = 11.sp,
                    )
                }

                LabeledSwitchRow(
                    title = "Grabar automáticamente al despegar",
                    subtitle = if (settings.videoAutorecordKnown) {
                        if (settings.videoAutorecordEnabled) {
                            "Activo: el dron empieza a grabar al iniciar vuelo"
                        } else {
                            "Desactivado: graba solo cuando pulses Dron REC"
                        }
                    } else {
                        "Estado pendiente del dron (desactívalo para grabar manualmente)"
                    },
                    checked = settings.videoAutorecordEnabled,
                    enabled = droneConnected,
                    onCheckedChange = actions.onSetVideoAutorecord,
                )

                HorizontalDivider(color = HudColors.GlassBorder)

                Text(
                    text = "Modo de resolución (Dron + Celular)",
                    color = HudColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        VideoResolutionProfile.REC_720P_STREAM_720P,
                        VideoResolutionProfile.REC_1080P_STREAM_480P,
                    ).forEach { profile ->
                        val selected = settings.videoResolution == profile
                        Surface(
                            color = if (selected) HudColors.Accent.copy(alpha = 0.25f) else Color(0x520F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (selected) HudColors.Accent else HudColors.GlassBorder,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = canChangeRes) { actions.onSetResolution(profile) },
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = profile.label,
                                    color = if (selected) HudColors.Accent else HudColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = profile.detail,
                                    color = HudColors.TextSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "El Bebop 2 no permite transmitir 1080p al celular: elige 720p HD para grabar mejor calidad en el teléfono, o 1080p/480p para Full HD en el dron.",
                    color = HudColors.TextSecondary,
                    fontSize = 10.sp,
                )

                HorizontalDivider(color = HudColors.GlassBorder)
                SelectableOptionGroup(
                    title = "Velocidad de cuadros (FPS)",
                    options = listOf(
                        VideoFramerateMode.FPS_24,
                        VideoFramerateMode.FPS_25,
                        VideoFramerateMode.FPS_30,
                    ),
                    selected = settings.videoFramerate,
                    labelOf = { it.label },
                    enabled = droneConnected && (!fw.isKnown || fw.supportsVideoResolutionControls),
                    onSelect = actions.onSetFramerate,
                )

                SelectableOptionGroup(
                    title = "Calidad en memoria del dron",
                    options = listOf(
                        VideoRecordingQualityMode.QUALITY,
                        VideoRecordingQualityMode.TIME,
                    ),
                    selected = settings.videoRecordingMode,
                    labelOf = { it.label },
                    enabled = droneConnected && (!fw.isKnown || fw.supportsVideoResolutionControls),
                    onSelect = actions.onSetRecordingMode,
                )

                SelectableOptionGroup(
                    title = "Transmisión en vivo al celular",
                    options = listOf(
                        VideoStreamReliabilityMode.LOW_LATENCY,
                        VideoStreamReliabilityMode.HIGH_RELIABILITY,
                        VideoStreamReliabilityMode.HIGH_RELIABILITY_LOW_FPS,
                    ),
                    selected = settings.videoStreamMode,
                    labelOf = { it.label },
                    enabled = droneConnected,
                    onSelect = actions.onSetStreamMode,
                )

                SelectableOptionGroup(
                    title = "Estabilización digital",
                    options = listOf(
                        VideoStabilizationMode.ROLL_PITCH,
                        VideoStabilizationMode.PITCH,
                        VideoStabilizationMode.ROLL,
                        VideoStabilizationMode.NONE,
                    ),
                    selected = settings.videoStabilization,
                    labelOf = { it.label },
                    enabled = droneConnected && (!fw.isKnown || fw.supportsVideoResolutionControls),
                    onSelect = actions.onSetStabilization,
                )
            }
        },
        rightColumn = {
            PanelSectionCard(title = "Exposición real (Bebop 2)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Exposición Automática (AUTO)",
                            color = HudColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Ajuste dinámico de EV según luz de escena",
                            color = HudColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = settings.autoExposureEnabled,
                        onCheckedChange = actions.onSetAutoExposure,
                        enabled = droneConnected,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF082F49),
                            checkedTrackColor = HudColors.Accent,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (settings.exposureKnown) {
                            String.format(Locale.US, "EV actual: %+.2f", settings.exposureValue)
                        } else {
                            "EV actual: N/D"
                        },
                        color = HudColors.TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = settings.estimatedLuminance?.let {
                            String.format(Locale.US, "Lum: %.2f", it)
                        } ?: "Lum: --",
                        color = HudColors.Accent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                if (!settings.autoExposureEnabled) {
                    Slider(
                        value = clampedEv,
                        onValueChange = actions.onSetManualEv,
                        valueRange = safeMin..safeMax,
                        enabled = droneConnected,
                        colors = SliderDefaults.colors(
                            thumbColor = HudColors.Accent,
                            activeTrackColor = HudColors.Accent,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = String.format(Locale.US, "Rango: [%.1f .. +%.1f]", safeMin, safeMax),
                            color = HudColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        OutlinedButton(
                            onClick = { actions.onSetAutoExposure(true) },
                            enabled = droneConnected,
                        ) {
                            Text("Volver a AUTO", color = HudColors.Accent, fontSize = 11.sp)
                        }
                    }
                }
            }

            PanelSectionCard(title = "Color y Balance de Blancos") {
                BoundedSliderRow(
                    title = "Saturación de color",
                    subtitle = "Intensidad de color del sensor del dron",
                    setting = settings.saturation,
                    unit = "",
                    decimals = 0,
                    enabled = droneConnected,
                    onCommit = actions.onSetSaturation,
                )
                HorizontalDivider(color = HudColors.GlassBorder)
                SelectableOptionGroup(
                    title = "Balance de blancos",
                    options = listOf(
                        WhiteBalanceMode.AUTO,
                        WhiteBalanceMode.DAYLIGHT,
                        WhiteBalanceMode.CLOUDY,
                        WhiteBalanceMode.TUNGSTEN,
                        WhiteBalanceMode.COOL_WHITE,
                    ),
                    selected = settings.whiteBalanceMode,
                    labelOf = { it.label },
                    enabled = droneConnected,
                    onSelect = actions.onSetWhiteBalance,
                )
            }

            PanelSectionCard(title = "Brillo de pantalla (solo celular)") {
                val customBrightness = settings.localPreviewBrightness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = customBrightness?.let { "Manual ${(it * 100).toInt()}%" } ?: "Brillo del sistema",
                        color = HudColors.TextPrimary,
                        fontSize = 12.sp,
                    )
                    if (customBrightness != null) {
                        Text(
                            text = "Restaurar",
                            color = HudColors.Accent,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { actions.onSetLocalBrightness(null) },
                        )
                    }
                }
                Slider(
                    value = customBrightness ?: 0.7f,
                    onValueChange = { actions.onSetLocalBrightness(it.coerceIn(0.15f, 1.0f)) },
                    valueRange = 0.15f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = HudColors.WarnAmber,
                        activeTrackColor = HudColors.WarnAmber,
                    ),
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SelectableOptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = HudColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    color = if (isSelected) HudColors.Accent.copy(alpha = 0.25f) else Color(0x520F172A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HudColors.Accent else HudColors.GlassBorder,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable(enabled = enabled) { onSelect(option) },
                ) {
                    Text(
                        text = labelOf(option),
                        color = if (isSelected) HudColors.Accent else HudColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryContent(
    uiState: BebopPilotUiState,
    onExportFlightLog: () -> Unit,
) {
    val t = uiState.telemetry
    val rec = uiState.droneRecording
    val vm = uiState.videoMetrics
    val fw = uiState.bebop2Firmware
    val log = uiState.flightLog

    ResponsiveTwoColumnScroll(
        leftColumn = {
            PanelSectionCard(title = "Dinámica y Actitud (real)") {
                TelemetryRow("Altitud rel.", t.altitudeMeters?.let { String.format(Locale.US, "%.2f m", it) })
                TelemetryRow(
                    "Velocidad XYZ",
                    if (t.speedX != null && t.speedY != null && t.speedZ != null) {
                        String.format(Locale.US, "%.2f / %.2f / %.2f m/s", t.speedX, t.speedY, t.speedZ)
                    } else null,
                )
                TelemetryRow("Roll", t.rollRad?.let { String.format(Locale.US, "%.1f°", it * 180f / PI.toFloat()) })
                TelemetryRow("Pitch", t.pitchRad?.let { String.format(Locale.US, "%.1f°", it * 180f / PI.toFloat()) })
                TelemetryRow("Yaw", t.yawRad?.let { String.format(Locale.US, "%.1f°", it * 180f / PI.toFloat()) })
                TelemetryRow("Estado vuelo", uiState.bebop2FlyingState)
                TelemetryRow("Retorno a casa", uiState.flightSettings.navigateHomeState)
                uiState.flightSettings.alertState?.let { alert ->
                    TelemetryRow("Alerta vuelo", alert, valueColor = HudColors.RecRed)
                }
            }

            PanelSectionCard(title = "GPS y Enlace") {
                TelemetryRow(
                    "Fix GPS",
                    when (t.gpsFixed) {
                        true -> "Válido"
                        false -> "Sin fix"
                        null -> null
                    },
                )
                TelemetryRow("Satélites", t.gpsSatellites?.toString())
                TelemetryRow(
                    "Coordenadas",
                    if (t.latitude != null && t.longitude != null) {
                        String.format(Locale.US, "%.6f, %.6f", t.latitude, t.longitude)
                    } else null,
                )
                TelemetryRow("Altitud GPS", t.gpsAltitude?.let { String.format(Locale.US, "%.1f m", it) })
                TelemetryRow("Señal SC2", t.skyControllerSignalLevel?.let { "$it / 5" })
                TelemetryRow("Wi-Fi Bebop", t.droneWifiRssiDbm?.let { "$it dBm" })
            }

            PanelSectionCard(title = "Sensores reportados") {
                if (t.sensors.isEmpty()) {
                    Text(
                        text = "Sin reportes de sensores recibidos todavía.",
                        color = HudColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                } else {
                    t.sensors.forEach { sensor ->
                        TelemetryRow(
                            label = sensor.name,
                            value = if (sensor.ok) "OK" else "Fallo",
                            valueColor = if (sensor.ok) HudColors.OkGreen else HudColors.RecRed,
                        )
                    }
                }
            }
        },
        rightColumn = {
            PanelSectionCard(title = "Bitácora de Vuelo Automática (CSV)") {
                TelemetryRow("Archivo actual", log.activeFileName ?: "Sin sesión")
                TelemetryRow("Muestras / Eventos", "${log.sampleCount} / ${log.eventCount}")
                if (log.recentEvents.isNotEmpty()) {
                    HorizontalDivider(color = HudColors.GlassBorder)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        log.recentEvents.take(6).forEach { ev ->
                            Text(
                                text = ev,
                                color = HudColors.TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                log.lastExportNotice?.let { notice ->
                    Text(
                        text = notice,
                        color = HudColors.OkGreen,
                        fontSize = 10.sp,
                    )
                }
                OutlinedButton(
                    onClick = onExportFlightLog,
                    enabled = log.activeFileName != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Guardar último CSV en Descargas", color = HudColors.Accent, fontSize = 11.sp)
                }
            }

            PanelSectionCard(title = "Batería, Memoria y Térmica") {
                TelemetryRow("Batería Bebop 2", uiState.bebop2BatteryPercent?.let { "$it%" })
                TelemetryRow("Batería SC2", uiState.skyControllerBatteryPercent?.let { "$it%" })
                TelemetryRow(
                    "Memoria libre",
                    if (rec.massStorageFreeMb != null && rec.massStorageTotalMb != null) {
                        "${rec.massStorageFreeMb} / ${rec.massStorageTotalMb} MB"
                    } else null,
                )
                TelemetryRow(
                    "Tiempo disp. REC",
                    rec.remainingRecTimeSec?.let { formatDuration(it) },
                )
                TelemetryRow("Regulación térmica", t.overheatRegulation)
            }

            PanelSectionCard(title = "Diagnóstico de Vídeo (real)") {
                TelemetryRow("Estado códec", vm.state.label)
                TelemetryRow(
                    "Resolución flujo (SPS)",
                    formatResolutionLabel(vm.spsWidth, vm.spsHeight),
                )
                TelemetryRow(
                    "Resolución salida",
                    formatResolutionLabel(vm.outputWidth, vm.outputHeight),
                )
                TelemetryRow(
                    "FPS recibidos (RX)",
                    vm.receivedFps?.let { "${formatFpsLabel(it, decimals = 1)} fps" } ?: "N/D",
                )
                TelemetryRow(
                    "FPS decodificados (DEC)",
                    vm.decodedFps?.let { "${formatFpsLabel(it, decimals = 1)} fps" } ?: "N/D",
                )
                TelemetryRow("Bitrate real", formatBitrateLabel(vm.bitrateKbps))
                TelemetryRow(
                    "Fotogramas perdidos",
                    formatMissedFramesLabel(vm),
                    valueColor = if (vm.missedFrames > 0L) HudColors.WarnAmber else HudColors.TextPrimary,
                )
                TelemetryRow(
                    "Frames RX / Dec",
                    if (vm.totalFramesReceived > 0L || vm.decodedFrames > 0L) {
                        "${vm.totalFramesReceived} / ${vm.decodedFrames}"
                    } else {
                        "N/D"
                    },
                )
                if (vm.decoderError != null) {
                    TelemetryRow(
                        "Error decodificador",
                        vm.decoderError,
                        valueColor = HudColors.RecRed,
                    )
                }
            }

            PanelSectionCard(title = "Enlace USB / MUX / SDK") {
                TelemetryRow("USB Accessory", uiState.usbState.label)
                TelemetryRow("MUX", uiState.muxState.label)
                TelemetryRow("Skycontroller 2", uiState.skyControllerState.label)
                TelemetryRow(
                    "Firmware SW Bebop 2",
                    if (fw.isKnown) fw.softwareVersion else "N/D",
                )
                TelemetryRow(
                    "Hardware Bebop 2",
                    fw.hardwareVersion ?: "N/D",
                )
                TelemetryRow(
                    "Soporte >= 3.4.0",
                    when {
                        !fw.isKnown -> "N/D"
                        fw.supportsVideoResolutionControls -> "Sí"
                        else -> "No (< 3.4.0)"
                    },
                )
            }
        },
    )
}

private enum class GallerySourceTab {
    LOCAL_PHONE,
    DRONE_STORAGE,
}

@Composable
private fun GalleryContent(
    uiState: BebopPilotUiState,
    onRefreshGallery: () -> Unit,
    onRefreshLocalGallery: () -> Unit,
    onDownloadMedia: (DroneMediaItem) -> Unit,
    onDeleteDroneMedia: (DroneMediaItem) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val gallery = uiState.gallery
    val droneConnected = uiState.bebop2State == Bebop2LinkState.CONNECTED
    val isDroneBusyRecording = uiState.droneRecording.state == DroneRecordState.RECORDING ||
        uiState.droneRecording.state == DroneRecordState.STARTING ||
        uiState.droneRecording.state == DroneRecordState.STOPPING
    val isFtpBusy = gallery.isLoading || gallery.isDownloading || gallery.isDeleting
    var selectedSource by remember { mutableStateOf(GallerySourceTab.LOCAL_PHONE) }
    var viewingItem by remember { mutableStateOf<LocalMediaItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<DroneMediaItem?>(null) }

    LaunchedEffect(Unit) {
        onRefreshLocalGallery()
    }

    val localByName = remember(gallery.localItems) {
        gallery.localItems.associateBy { it.name }
    }

    Column(modifier = Modifier.fillMaxHeight()) {
        // Barra superior compacta: Selector Celular / Dron + Actualizar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                GallerySourceTab.LOCAL_PHONE to "En este celular (${gallery.localItems.size})",
                GallerySourceTab.DRONE_STORAGE to "Memoria del dron (${gallery.items.size})",
            ).forEach { (tab, title) ->
                val selected = selectedSource == tab
                Surface(
                    color = if (selected) HudColors.Accent.copy(alpha = 0.28f) else Color(0x520F172A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (selected) HudColors.Accent else HudColors.GlassBorder,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { selectedSource = tab },
                ) {
                    Text(
                        text = title,
                        color = if (selected) HudColors.Accent else HudColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Carpeta: Movies/BebopPilot · Pictures/BebopPilot",
                color = HudColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            OutlinedButton(
                onClick = {
                    if (selectedSource == GallerySourceTab.LOCAL_PHONE) {
                        onRefreshLocalGallery()
                    } else {
                        onRefreshGallery()
                    }
                },
                enabled = if (selectedSource == GallerySourceTab.LOCAL_PHONE) {
                    !gallery.localLoading
                } else {
                    droneConnected && !isFtpBusy
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                val busy = if (selectedSource == GallerySourceTab.LOCAL_PHONE) {
                    gallery.localLoading
                } else {
                    gallery.isLoading || gallery.isDeleting
                }
                Text(
                    text = when {
                        gallery.isDeleting -> "Borrando..."
                        busy -> "Cargando..."
                        else -> "Actualizar"
                    },
                    color = HudColors.Accent,
                    fontSize = 11.sp,
                )
            }
        }

        if (gallery.isDownloading) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0x6E0F172A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HudColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Guardando ${gallery.activeDownloadName.orEmpty()} (${gallery.downloadProgressPercent}%)",
                        color = HudColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        progress = { (gallery.downloadProgressPercent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.weight(1f),
                        color = HudColors.Accent,
                        trackColor = Color(0x520F172A),
                    )
                    Text(
                        text = "Cancelar",
                        color = HudColors.RecRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onCancelDownload),
                    )
                }
            }
        }

        gallery.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = err, color = HudColors.RecRed, fontSize = 11.sp)
        }
        gallery.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = msg, color = HudColors.OkGreen, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Encabezado tipo "Vista Detalles de Windows"
        Surface(
            color = Color(0x8A0B1220),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tipo", color = HudColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                Text("Nombre", color = HudColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (selectedSource == GallerySourceTab.LOCAL_PHONE) "Fecha · Duración" else "Fecha en dron",
                    color = HudColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(126.dp),
                )
                Text("Tamaño", color = HudColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Text(
                    "Estado / Acción",
                    color = HudColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(if (selectedSource == GallerySourceTab.LOCAL_PHONE) 136.dp else 174.dp),
                )
            }
        }

        // Cuerpo de la lista tipo Detalles
        Surface(
            color = Color(0x4D09101C),
            shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
        ) {
            if (selectedSource == GallerySourceTab.LOCAL_PHONE) {
                if (gallery.localItems.isEmpty() && !gallery.localLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No hay fotos o videos de BebopPilot en este celular todavía.",
                            color = HudColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(gallery.localItems.size, key = { idx ->
                            val it = gallery.localItems[idx]
                            "loc_${it.id}_${it.uriString}"
                        }) { idx ->
                            val item = gallery.localItems[idx]
                            val rowBg = if (idx % 2 == 0) Color(0x380F172A) else Color(0x5217253B)
                            val dateStr = remember(item.dateAddedSec) {
                                if (item.dateAddedSec > 0L) dateFmt.format(Date(item.dateAddedSec * 1000L)) else "--"
                            }
                            val durStr = item.durationMs?.takeIf { it > 0L }?.let { " · ${formatDuration(it / 1000L)}" } ?: ""

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable { viewingItem = item },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (item.isVideo) "MP4" else "JPG",
                                        color = if (item.isVideo) HudColors.Accent else HudColors.WarnAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(50.dp),
                                    )
                                    Text(
                                        text = item.name,
                                        color = HudColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                                    )
                                    Text(
                                        text = "$dateStr$durStr",
                                        color = HudColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        modifier = Modifier.width(126.dp),
                                    )
                                    Text(
                                        text = formatFileSizeLabel(item.sizeBytes),
                                        color = HudColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Row(
                                        modifier = Modifier.width(136.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "✓ En cel",
                                            color = HudColors.OkGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Surface(
                                            color = HudColors.Accent.copy(alpha = 0.24f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .border(1.dp, HudColors.Accent, RoundedCornerShape(6.dp))
                                                .clickable { viewingItem = item },
                                        ) {
                                            Text(
                                                text = if (item.isVideo) "Ver ▶" else "Ver",
                                                color = HudColors.Accent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0x1EFFFFFF))
                            }
                        }
                    }
                }
            } else {
                if (gallery.items.isEmpty() && !gallery.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (droneConnected) {
                                "Pulsa \"Actualizar\" para listar los videos y fotos internos del Bebop 2."
                            } else {
                                "Conecta el Bebop 2 para acceder a su memoria interna."
                            },
                            color = HudColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(gallery.items.size, key = { idx ->
                            val it = gallery.items[idx]
                            "drn_${it.name}_${it.uuid}"
                        }) { idx ->
                            val item = gallery.items[idx]
                            val matchedLocal = localByName[item.name]
                            val alreadySaved = matchedLocal != null || (item.name in gallery.downloadedNames)
                            val rowBg = if (idx % 2 == 0) Color(0x380F172A) else Color(0x5217253B)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (item.isVideo) "MP4" else "JPG",
                                        color = if (item.isVideo) HudColors.Accent else HudColors.WarnAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(50.dp),
                                    )
                                    Text(
                                        text = item.name,
                                        color = HudColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                                    )
                                    Text(
                                        text = item.date.ifBlank { "--" },
                                        color = HudColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(126.dp),
                                    )
                                    Text(
                                        text = formatFileSizeLabel(item.sizeBytes),
                                        color = HudColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Row(
                                        modifier = Modifier.width(174.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (alreadySaved) {
                                            if (matchedLocal != null) {
                                                Surface(
                                                    color = HudColors.OkGreen.copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .border(1.dp, HudColors.OkGreen, RoundedCornerShape(6.dp))
                                                        .clickable { viewingItem = matchedLocal },
                                                ) {
                                                    Text(
                                                        text = if (matchedLocal.isVideo) "✓ Ver ▶" else "✓ Ver",
                                                        color = HudColors.OkGreen,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "✓ En cel",
                                                    color = HudColors.OkGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        } else {
                                            val canSave = droneConnected && !isFtpBusy
                                            Surface(
                                                color = if (canSave) HudColors.Accent else Color(0x40475569),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .clickable(enabled = canSave) { onDownloadMedia(item) },
                                            ) {
                                                Text(
                                                    text = "Guardar",
                                                    color = if (canSave) Color(0xFF082F49) else HudColors.TextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }
                                        }

                                        val canDelete = droneConnected && !isFtpBusy && !isDroneBusyRecording
                                        Surface(
                                            color = if (canDelete) HudColors.RecRed.copy(alpha = 0.22f) else Color(0x33475569),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .border(
                                                    1.dp,
                                                    if (canDelete) HudColors.RecRed.copy(alpha = 0.85f) else Color(0x33FFFFFF),
                                                    RoundedCornerShape(6.dp),
                                                )
                                                .clickable(enabled = canDelete) { pendingDeleteItem = item },
                                        ) {
                                            Text(
                                                text = "Borrar",
                                                color = if (canDelete) HudColors.RecRed else HudColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0x1EFFFFFF))
                            }
                        }
                    }
                }
            }
        }
    }

    viewingItem?.let { item ->
        InAppMediaViewerModal(
            item = item,
            onDismiss = { viewingItem = null },
        )
    }

    pendingDeleteItem?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            containerColor = Color(0xF00F172A),
            title = {
                Text(
                    text = "¿Eliminar del Bebop 2?",
                    color = HudColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Se borrará permanentemente \"${target.name}\" (${formatFileSizeLabel(target.sizeBytes)}) de la memoria interna del dron.\n\nLas copias que ya guardaste en tu celular no se tocarán.",
                    color = HudColors.TextSecondary,
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteItem = null
                        onDeleteDroneMedia(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HudColors.RecRed),
                ) {
                    Text("Sí, borrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("Cancelar", color = HudColors.Accent)
                }
            },
        )
    }
}

private fun formatFileSizeLabel(bytes: Long): String {
    if (bytes < 1024L * 1024L) {
        val kb = (bytes / 1024.0).coerceAtLeast(1.0)
        return String.format(Locale.US, "%.0f KB", kb)
    }
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun InAppMediaViewerModal(
    item: LocalMediaItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var imageBitmap by remember(item.uriString) { mutableStateOf<Bitmap?>(null) }
    var mediaError by remember(item.uriString) { mutableStateOf<String?>(null) }

    if (!item.isVideo) {
        LaunchedEffect(item.uriString) {
            mediaError = null
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(item.uriString)
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, boundsOpts)
                    }
                    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                        error("Archivo sin cabecera de imagen válida (${item.name})")
                    }
                    var sample = 1
                    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                    while (maxDim / sample > 2048) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    } ?: error("El decodificador devolvió imagen vacía")
                }
            }
            decoded.onSuccess { bmp ->
                imageBitmap = bmp
            }.onFailure { t ->
                mediaError = "Imagen dañada o no compatible: ${t.message ?: item.name}"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = Color(0xEB05080F),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "${if (item.isVideo) "[MP4]" else "[JPG]"} ${item.name}",
                            color = HudColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${item.relativePath} · ${formatFileSizeLabel(item.sizeBytes)}",
                            color = HudColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(item.uriString),
                                        if (item.isVideo) "video/mp4" else "image/*",
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            },
                        ) {
                            Text("Abrir externa", color = HudColors.TextPrimary, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent),
                        ) {
                            Text("Cerrar ✕", color = Color(0xFF082F49), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mediaError != null) {
                        Text(
                            text = mediaError ?: "",
                            color = HudColors.RecRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else if (item.isVideo) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    val videoView = VideoView(ctx).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.Gravity.CENTER,
                                        )
                                        val mc = MediaController(ctx)
                                        mc.setAnchorView(this)
                                        setMediaController(mc)
                                        setVideoURI(Uri.parse(item.uriString))
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            start()
                                        }
                                        setOnErrorListener { _, what, extra ->
                                            mediaError = "Error reproduciendo MP4 ($what/$extra)"
                                            true
                                        }
                                        requestFocus()
                                    }
                                    addView(videoView)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val bmp = imageBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                            )
                        } else {
                            Text("Cargando imagen...", color = HudColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = HudColors.CardBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(14.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = HudColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String?,
    valueColor: Color = HudColors.TextPrimary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = HudColors.TextSecondary,
            fontSize = 11.sp,
        )
        Text(
            text = value ?: "No disponible",
            color = if (value != null) valueColor else HudColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MagnetoCalibrationGuideBox(
    axis: MagnetoAxis,
    isLive: Boolean,
) {
    Surface(
        color = Color(0x66091220),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HudColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MagnetoAxisAnimationCanvas(
                axis = if (axis == MagnetoAxis.NONE) MagnetoAxis.X_AXIS else axis,
                modifier = Modifier
                    .width(116.dp)
                    .height(116.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isLive) "MOVIMIENTO ACTIVO · ${axis.label}" else "VISTA PREVIA · ${axis.label}",
                    color = HudColors.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = axis.shortHint,
                    color = HudColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = axis.instruction,
                    color = HudColors.TextSecondary,
                    fontSize = 10.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(HudColors.Accent, androidx.compose.foundation.shape.CircleShape),
                        )
                        Text("Cámara (Frente)", color = HudColors.TextSecondary, fontSize = 9.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(HudColors.RecRed, androidx.compose.foundation.shape.CircleShape),
                        )
                        Text("Batería (Atrás)", color = HudColors.TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MagnetoAxisAnimationCanvas(
    axis: MagnetoAxis,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "magnetoDroneAnim")
    val angleDeg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "magnetoRotationDeg",
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.44f
        val center = Offset(cx, cy)

        // Aro guía circular con flecha indicando el sentido de rotación
        val arcTopLeft = Offset(cx - r, cy - r)
        val arcSize = Size(r * 2f, r * 2f)
        drawCircle(
            color = Color(0x28FFFFFF),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        val sweep = if (axis == MagnetoAxis.Y_AXIS) -220f else 220f
        val startAngle = if (axis == MagnetoAxis.Y_AXIS) -25f else -145f
        drawArc(
            color = HudColors.Accent.copy(alpha = 0.85f),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )

        // Punta de flecha al final del arco
        val endRad = ((startAngle + sweep) * PI / 180.0).toFloat()
        val tip = Offset(cx + r * cos(endRad), cy + r * sin(endRad))
        val tangentDir = if (sweep > 0f) endRad + (PI / 2.0).toFloat() else endRad - (PI / 2.0).toFloat()
        val arrowLen = 7.dp.toPx()
        val arrowPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(
                tip.x - arrowLen * cos(tangentDir - 0.55f),
                tip.y - arrowLen * sin(tangentDir - 0.55f),
            )
            moveTo(tip.x, tip.y)
            lineTo(
                tip.x - arrowLen * cos(tangentDir + 0.55f),
                tip.y - arrowLen * sin(tangentDir + 0.55f),
            )
        }
        drawPath(
            path = arrowPath,
            color = HudColors.Accent,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )

        val bodyColor = Color(0xFFE2E8F0)
        val darkHull = Color(0xFF1E293B)
        val accentCyan = HudColors.Accent
        val rearRed = HudColors.RecRed

        when (axis) {
            MagnetoAxis.X_AXIS -> {
                // Vista frontal: cámara mirando hacia ti, gira lateralmente como barrena (Roll)
                rotate(degrees = angleDeg, pivot = center) {
                    val wingHalf = r * 0.68f
                    // Brazos horizontales
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx - wingHalf, cy),
                        end = Offset(cx + wingHalf, cy),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Patas inferiores para distinguir derecho vs boca abajo
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx - wingHalf * 0.55f, cy),
                        end = Offset(cx - wingHalf * 0.62f, cy + r * 0.28f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx + wingHalf * 0.55f, cy),
                        end = Offset(cx + wingHalf * 0.62f, cy + r * 0.28f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Hélices izquierda y derecha vistas de frente
                    drawLine(
                        color = accentCyan,
                        start = Offset(cx - wingHalf - r * 0.2f, cy - r * 0.08f),
                        end = Offset(cx - wingHalf + r * 0.2f, cy - r * 0.08f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentCyan,
                        start = Offset(cx + wingHalf - r * 0.2f, cy - r * 0.08f),
                        end = Offset(cx + wingHalf + r * 0.2f, cy - r * 0.08f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Fuselaje central y lente de cámara frontal
                    drawRoundRect(
                        color = darkHull,
                        topLeft = Offset(cx - r * 0.24f, cy - r * 0.18f),
                        size = Size(r * 0.48f, r * 0.36f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                    drawRoundRect(
                        color = bodyColor,
                        topLeft = Offset(cx - r * 0.24f, cy - r * 0.18f),
                        size = Size(r * 0.48f, r * 0.36f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                    drawCircle(color = accentCyan, radius = r * 0.11f, center = center)
                    drawCircle(color = Color(0xFF082F49), radius = r * 0.05f, center = center)
                }
            }

            MagnetoAxis.Y_AXIS -> {
                // Vista lateral (perfil): nariz/cámara a la derecha, batería atrás izquierda -> voltereta Pitch
                rotate(degrees = -angleDeg, pivot = center) {
                    val halfLen = r * 0.62f
                    // Cuerpo alargado
                    drawRoundRect(
                        color = darkHull,
                        topLeft = Offset(cx - halfLen * 0.85f, cy - r * 0.13f),
                        size = Size(halfLen * 1.7f, r * 0.26f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                    drawRoundRect(
                        color = bodyColor,
                        topLeft = Offset(cx - halfLen * 0.85f, cy - r * 0.13f),
                        size = Size(halfLen * 1.7f, r * 0.26f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    // Batería roja en parte trasera (izquierda)
                    drawRoundRect(
                        color = rearRed,
                        topLeft = Offset(cx - halfLen * 0.92f, cy - r * 0.11f),
                        size = Size(r * 0.2f, r * 0.22f),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                    // Cámara cian en nariz (derecha) + flecha frente
                    drawCircle(
                        color = accentCyan,
                        radius = r * 0.13f,
                        center = Offset(cx + halfLen * 0.82f, cy),
                    )
                    // Patas abajo
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx - halfLen * 0.45f, cy + r * 0.13f),
                        end = Offset(cx - halfLen * 0.55f, cy + r * 0.30f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = bodyColor,
                        start = Offset(cx + halfLen * 0.45f, cy + r * 0.13f),
                        end = Offset(cx + halfLen * 0.55f, cy + r * 0.30f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Hélices arriba adelante y atrás
                    drawLine(
                        color = accentCyan,
                        start = Offset(cx - halfLen * 0.78f, cy - r * 0.22f),
                        end = Offset(cx - halfLen * 0.32f, cy - r * 0.22f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentCyan,
                        start = Offset(cx + halfLen * 0.32f, cy - r * 0.22f),
                        end = Offset(cx + halfLen * 0.78f, cy - r * 0.22f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            MagnetoAxis.Z_AXIS, MagnetoAxis.NONE -> {
                // Vista superior (planta): X-frame plano rotando horizontalmente (Yaw)
                rotate(degrees = angleDeg, pivot = center) {
                    val arm = r * 0.48f
                    val motors = listOf(
                        Offset(cx - arm, cy - arm),
                        Offset(cx + arm, cy - arm),
                        Offset(cx - arm, cy + arm),
                        Offset(cx + arm, cy + arm),
                    )
                    motors.forEach { m ->
                        drawLine(
                            color = bodyColor,
                            start = center,
                            end = m,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawCircle(
                            color = accentCyan.copy(alpha = 0.20f),
                            radius = r * 0.23f,
                            center = m,
                        )
                        drawCircle(
                            color = accentCyan,
                            radius = r * 0.23f,
                            center = m,
                            style = Stroke(width = 1.4.dp.toPx()),
                        )
                    }
                    // Cuerpo central orientado arriba=frente, abajo=atrás
                    drawRoundRect(
                        color = darkHull,
                        topLeft = Offset(cx - r * 0.15f, cy - r * 0.44f),
                        size = Size(r * 0.30f, r * 0.88f),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                    )
                    drawRoundRect(
                        color = bodyColor,
                        topLeft = Offset(cx - r * 0.15f, cy - r * 0.44f),
                        size = Size(r * 0.30f, r * 0.88f),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                    // Nariz cámara arriba (cian) + batería abajo (rojo)
                    drawCircle(
                        color = accentCyan,
                        radius = r * 0.11f,
                        center = Offset(cx, cy - r * 0.42f),
                    )
                    drawRoundRect(
                        color = rearRed,
                        topLeft = Offset(cx - r * 0.10f, cy + r * 0.28f),
                        size = Size(r * 0.20f, r * 0.15f),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutContent(uiState: BebopPilotUiState) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val packageInfo = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "0.1.0"
    val versionCode = remember(packageInfo) {
        if (packageInfo == null) {
            1L
        } else {
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        }
    }
    val repoUrl = "https://github.com/Miascn/BebopPilot"

    ResponsiveTwoColumnScroll(
        leftColumn = {
            PanelSectionCard(title = "BebopPilot · Información de la App") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bebop_pilot_logo),
                        contentDescription = "Icono de BebopPilot",
                        modifier = Modifier
                            .width(68.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, HudColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "BebopPilot",
                            color = HudColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Estación FPV y telemetría USB para Parrot Bebop 2 + Skycontroller 2",
                            color = HudColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                        Surface(
                            color = HudColors.Accent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, HudColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        ) {
                            Text(
                                text = "Versión $versionName (Build $versionCode)",
                                color = HudColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = HudColors.GlassBorder)
                TelemetryRow("Creada por", "MiasCN", valueColor = HudColors.Accent)
                TelemetryRow("Paquete", context.packageName)
                TelemetryRow("Arquitectura", "Android 8.0+ (API 26+) · arm64-v8a")
                TelemetryRow("Motor nativo", "Parrot ARSDK3 3.13.1 (USB Accessory MUX)")
            }

            PanelSectionCard(title = "Hardware y Enlace Detectado") {
                TelemetryRow(
                    "Skycontroller 2",
                    uiState.skyControllerName ?: uiState.skyControllerState.label,
                )
                TelemetryRow(
                    "Dron",
                    uiState.bebop2Name ?: uiState.bebop2State.label,
                )
                TelemetryRow("Firmware Bebop 2", uiState.bebop2Firmware.softwareVersion)
                TelemetryRow("Hardware Bebop 2", uiState.bebop2Firmware.hardwareVersion)
                TelemetryRow("Estado MUX USB", uiState.muxDetail ?: uiState.muxState.label)
            }
        },
        rightColumn = {
            PanelSectionCard(title = "Capacidades y Diseño") {
                val features = listOf(
                    "Vídeo en vivo H.264 decodificado por hardware (MediaCodec) con métricas RX/DEC en tiempo real.",
                    "Control completo de vuelo y cámara: presets de velocidad, curvas Expo del Skycontroller 2, RTH, FlatTrim, EV, balance de blancos y saturación.",
                    "Grabación simultánea en memoria interna del Bebop 2 y MP4 local transcodificado en el teléfono, además de fotos DNG/JPG.",
                    "Galería unificada (en el celular y en la memoria del dron) con descarga, borrado remoto y visor integrado.",
                    "Calibración magnética de 3 ejes asistida, alerta de batería baja <= 15 %, bitácora automática CSV y reconexión USB automática al volver de segundo plano.",
                )
                features.forEach { item ->
                    Text(
                        text = "• $item",
                        color = HudColors.TextPrimary,
                        fontSize = 11.sp,
                    )
                }
            }

            PanelSectionCard(title = "Autoría y Repositorio Público") {
                Text(
                    text = "Aplicación diseñada y desarrollada por MiasCN para operación directa por cable USB con Skycontroller 2, sin telemetría externa ni servicios de terceros.",
                    color = HudColors.TextSecondary,
                    fontSize = 11.sp,
                )
                TelemetryRow("Desarrollador", "MiasCN (@Miascn)", valueColor = HudColors.TextPrimary)
                TelemetryRow("Contacto", "josuejeremiascastillonieves@gmail.com")
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(repoUrl) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abrir repositorio en GitHub · $repoUrl", color = HudColors.Accent, fontSize = 11.sp)
                }
            }
        },
    )
}
