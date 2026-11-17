package com.beboppilot.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beboppilot.app.sdk.Bebop2LinkState
import com.beboppilot.app.sdk.BebopPilotUiState
import com.beboppilot.app.sdk.DroneRecordState
import com.beboppilot.app.sdk.LocalRecordState
import com.beboppilot.app.sdk.OverallConnectionPhase
import com.beboppilot.app.sdk.PhotoCaptureState
import com.beboppilot.app.sdk.UsbConnectionState
import com.beboppilot.app.sdk.VideoMetrics
import com.beboppilot.app.sdk.VideoResolutionProfile
import com.beboppilot.app.sdk.VideoStreamState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ActiveSidePanel {
    NONE,
    FLIGHT_SETTINGS,
    CAMERA_SETTINGS,
    TELEMETRY,
    GALLERY,
    ABOUT,
}

internal object HudColors {
    val GlassBg = Color(0x9E0B1018)
    val GlassBorder = Color(0x47FFFFFF)
    val PanelBg = Color(0x54070C14)
    val CardBg = Color(0x6B0F172A)
    val Accent = Color(0xFF38BDF8)
    val RecRed = Color(0xFFEF4444)
    val LocalRecAmber = Color(0xFFF59E0B)
    val OkGreen = Color(0xFF22C55E)
    val WarnAmber = Color(0xFFFBBF24)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFCBD5E1)
}

internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600L
    val mins = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

internal fun formatResolutionLabel(width: Int?, height: Int?): String =
    if (width != null && height != null && width > 0 && height > 0) "${width}x${height}" else "N/D"

internal fun formatFpsLabel(fps: Float?, decimals: Int = 0): String =
    fps?.let {
        if (decimals <= 0) String.format(Locale.US, "%.0f", it)
        else String.format(Locale.US, "%.${decimals}f", it)
    } ?: "N/D"

internal fun formatBitrateLabel(bitrateKbps: Int?): String =
    bitrateKbps?.let { "$it kbps" } ?: "N/D"

internal fun formatMissedFramesLabel(metrics: VideoMetrics): String =
    if (metrics.totalFramesReceived > 0L || metrics.missedFrames > 0L) {
        metrics.missedFrames.toString()
    } else {
        "N/D"
    }

@Composable
fun CompactTopHudBar(
    uiState: BebopPilotUiState,
    modifier: Modifier = Modifier,
) {
    val phase = uiState.overallPhase
    val phaseColor = when (phase) {
        OverallConnectionPhase.RECEIVING_VIDEO -> HudColors.OkGreen
        OverallConnectionPhase.BEBOP2_CONNECTED,
        OverallConnectionPhase.MUX_CONNECTED,
        OverallConnectionPhase.SEARCHING_BEBOP2,
        OverallConnectionPhase.USB_CONNECTED -> HudColors.Accent
        OverallConnectionPhase.MUX_INITIALIZING,
        OverallConnectionPhase.USB_PERMISSION_PENDING -> HudColors.WarnAmber
        OverallConnectionPhase.USB_DISCONNECTED,
        OverallConnectionPhase.ERROR -> HudColors.RecRed
    }
    val friendlyStatus = when (phase) {
        OverallConnectionPhase.RECEIVING_VIDEO -> "En vivo"
        OverallConnectionPhase.BEBOP2_CONNECTED -> "Dron listo"
        OverallConnectionPhase.SEARCHING_BEBOP2,
        OverallConnectionPhase.MUX_CONNECTED -> "Buscando dron"
        OverallConnectionPhase.USB_CONNECTED,
        OverallConnectionPhase.MUX_INITIALIZING -> "Conectando mando"
        OverallConnectionPhase.USB_PERMISSION_PENDING -> "Permiso USB"
        OverallConnectionPhase.USB_DISCONNECTED -> "Sin conexión"
        OverallConnectionPhase.ERROR -> "Error"
    }

    val isDroneRecording = uiState.droneRecording.state == DroneRecordState.RECORDING
    val localRecState = uiState.localRecording.state
    val isLocalRecording = localRecState == LocalRecordState.RECORDING ||
        localRecState == LocalRecordState.WAITING_KEYFRAME ||
        localRecState == LocalRecordState.WAITING_CONFIG ||
        localRecState == LocalRecordState.STOPPING

    val pulseTransition = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cápsula izquierda limpia: Estado + Señal + Mando
        Surface(
            color = HudColors.GlassBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(16.dp)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(phaseColor),
                )
                Text(
                    text = friendlyStatus,
                    color = HudColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (!uiState.bebop2Name.isNullOrBlank()) {
                    VerticalDividerPill()
                    Text(
                        text = uiState.bebop2Name,
                        color = HudColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val signalLevel = uiState.telemetry.skyControllerSignalLevel
                val rssi = uiState.telemetry.droneWifiRssiDbm
                if (signalLevel != null || rssi != null) {
                    VerticalDividerPill()
                    val bars = signalLevel?.coerceIn(0, 5) ?: when {
                        (rssi ?: -100) >= -60 -> 5
                        (rssi ?: -100) >= -70 -> 4
                        (rssi ?: -100) >= -78 -> 3
                        (rssi ?: -100) >= -86 -> 2
                        else -> 1
                    }
                    SignalBarsIcon(bars = bars, color = HudColors.Accent)
                }
                uiState.skyControllerBatteryPercent?.let { scPct ->
                    VerticalDividerPill()
                    Text(
                        text = "Mando $scPct%",
                        color = HudColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Cápsula derecha limpia: Calidad vídeo + Grabaciones activas + Batería estilo teléfono
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val vm = uiState.videoMetrics
            val resSummary = when {
                uiState.cameraSettings.videoResolution == VideoResolutionProfile.REC_1080P_STREAM_480P -> "1080p / 480p"
                uiState.cameraSettings.videoResolution == VideoResolutionProfile.REC_720P_STREAM_720P -> "720p HD"
                vm.effectiveHeight > 0 -> "${vm.effectiveHeight}p"
                else -> null
            }
            val fpsText = vm.decodedFps?.let { "${it.roundToInt()} fps" }
                ?: vm.receivedFps?.let { "${it.roundToInt()} fps" }

            if (resSummary != null || fpsText != null) {
                Surface(
                    color = HudColors.GlassBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(1.dp, HudColors.GlassBorder, RoundedCornerShape(14.dp)),
                ) {
                    val label = listOfNotNull(resSummary, fpsText).joinToString(" · ")
                    Text(
                        text = label,
                        color = HudColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (isDroneRecording) {
                Surface(
                    color = Color(0xCC450A0A),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(1.dp, HudColors.RecRed, RoundedCornerShape(14.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(HudColors.RecRed),
                        )
                        Text(
                            text = "Dron ${formatDuration(uiState.droneRecording.durationSec)}",
                            color = HudColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            if (isLocalRecording) {
                Surface(
                    color = Color(0xCC451A03),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(1.dp, HudColors.LocalRecAmber, RoundedCornerShape(14.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(HudColors.LocalRecAmber),
                        )
                        val hudLocalText = when (localRecState) {
                            LocalRecordState.WAITING_CONFIG -> "Móvil esp. señal"
                            LocalRecordState.WAITING_KEYFRAME -> "Móvil iniciando"
                            LocalRecordState.RECORDING -> "Móvil ${formatDuration(uiState.localRecording.durationSec)}"
                            LocalRecordState.STOPPING -> "Guardando MP4"
                            else -> "Móvil"
                        }
                        Text(
                            text = hudLocalText,
                            color = HudColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Icono de batería siempre visible estilo teléfono
            BatteryStatusBadge(
                percent = uiState.bebop2BatteryPercent,
                pulseAlpha = pulseAlpha,
            )
        }
    }
}

@Composable
internal fun BatteryStatusBadge(
    percent: Int?,
    pulseAlpha: Float = 1f,
) {
    val isCritical = percent != null && percent <= 15
    val color = when {
        percent == null -> HudColors.TextSecondary
        percent <= 15 -> HudColors.RecRed
        percent <= 30 -> HudColors.WarnAmber
        else -> HudColors.OkGreen
    }
    val borderColor = if (isCritical) HudColors.RecRed else HudColors.GlassBorder
    val bgColor = if (isCritical) Color(0xCC450A0A) else HudColors.GlassBg

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .alpha(if (isCritical) 0.65f + (0.35f * pulseAlpha) else 1f)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PhoneStyleBatteryIcon(
                percent = percent,
                fillColor = color,
            )
            Text(
                text = percent?.let { "$it%" } ?: "--%",
                color = if (percent == null) HudColors.TextSecondary else color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun LowBatteryAlarmBanner(
    batteryPercent: Int,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "battAlarm")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "battAlarmAlpha",
    )

    Surface(
        color = Color(0xE67F1D1D),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .alpha(alpha)
            .border(1.5.dp, HudColors.RecRed, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "BATERÍA BAJA ($batteryPercent%) — REGRESA A LA BASE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                color = Color(0x40000000),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleMute),
            ) {
                Text(
                    text = if (muted) "Silenciada" else "Silenciar",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun BottomCameraDock(
    uiState: BebopPilotUiState,
    activePanel: ActiveSidePanel,
    onCapturePhoto: () -> Unit,
    onToggleDroneRecording: () -> Unit,
    onToggleLocalRecording: () -> Unit,
    onToggleSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val droneConnected = uiState.bebop2State == Bebop2LinkState.CONNECTED
    val droneRecState = uiState.droneRecording.state
    val isDroneRecActive = droneRecState == DroneRecordState.RECORDING
    val isDroneRecBusy = uiState.droneRecording.isRequestPending ||
        droneRecState == DroneRecordState.STARTING ||
        droneRecState == DroneRecordState.STOPPING

    val localState = uiState.localRecording.state
    val isLocalRecActive = localState == LocalRecordState.RECORDING ||
        localState == LocalRecordState.WAITING_KEYFRAME ||
        localState == LocalRecordState.WAITING_CONFIG
    val isPhotoBusy = uiState.photoStatus.state == PhotoCaptureState.CAPTURING
    val settingsOpen = activePanel != ActiveSidePanel.NONE

    Surface(
        color = HudColors.GlassBg,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
            .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(22.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Foto
            DockIconButton(
                label = if (isPhotoBusy) "Foto..." else "Foto",
                active = uiState.photoStatus.state == PhotoCaptureState.TAKEN,
                activeColor = HudColors.OkGreen,
                enabled = droneConnected && !isPhotoBusy,
                onClick = onCapturePhoto,
                icon = { CameraGlyphIcon(color = if (droneConnected) HudColors.TextPrimary else HudColors.TextSecondary) },
            )

            // 2. Grabar en Dron
            DockIconButton(
                label = when {
                    isDroneRecBusy -> "Dron..."
                    isDroneRecActive -> formatDuration(uiState.droneRecording.durationSec)
                    else -> "Dron"
                },
                active = isDroneRecActive,
                activeColor = HudColors.RecRed,
                enabled = droneConnected && !isDroneRecBusy,
                onClick = onToggleDroneRecording,
                icon = {
                    RecordDotGlyphIcon(
                        recording = isDroneRecActive,
                        color = if (isDroneRecActive) Color.White else if (droneConnected) HudColors.RecRed else Color(0xFF64748B),
                    )
                },
            )

            // 3. Grabar en Celular (MP4 local)
            DockIconButton(
                label = when (localState) {
                    LocalRecordState.WAITING_CONFIG,
                    LocalRecordState.WAITING_KEYFRAME -> "Inic..."
                    LocalRecordState.RECORDING -> formatDuration(uiState.localRecording.durationSec)
                    LocalRecordState.STOPPING -> "Guard..."
                    else -> "Móvil"
                },
                active = isLocalRecActive,
                activeColor = HudColors.LocalRecAmber,
                enabled = (isLocalRecActive && localState != LocalRecordState.STOPPING) ||
                    uiState.videoMetrics.state == VideoStreamState.RECEIVING,
                onClick = onToggleLocalRecording,
                icon = {
                    RecordDotGlyphIcon(
                        recording = isLocalRecActive,
                        color = if (isLocalRecActive) Color.White else if (uiState.videoMetrics.state == VideoStreamState.RECEIVING) HudColors.LocalRecAmber else Color(0xFF64748B),
                    )
                },
            )

            VerticalDividerPill()

            // 4. Icono único de Configuración (contiene Vuelo, Cámara, Telemetría y Galería)
            Surface(
                color = if (settingsOpen) HudColors.Accent.copy(alpha = 0.25f) else Color(0xFF151C26),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(
                        width = 1.dp,
                        color = if (settingsOpen) HudColors.Accent else HudColors.GlassBorder,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggleSettings),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SettingsGearIcon(
                        color = if (settingsOpen) HudColors.Accent else HudColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockIconButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val bgColor = when {
        active -> activeColor.copy(alpha = 0.25f)
        enabled -> Color(0xFF151C26)
        else -> Color(0x9911161F)
    }
    val borderColor = if (active) activeColor else HudColors.GlassBorder

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            icon()
            Text(
                text = label,
                color = if (enabled || active) HudColors.TextPrimary else HudColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = if (active) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun PhoneStyleBatteryIcon(
    percent: Int?,
    fillColor: Color,
) {
    Canvas(modifier = Modifier.size(width = 24.dp, height = 13.dp)) {
        val stroke = 1.6.dp.toPx()
        val tipWidth = 2.5.dp.toPx()
        val bodyWidth = size.width - tipWidth - 1.dp.toPx()
        val bodyHeight = size.height

        drawRoundRect(
            color = fillColor,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(bodyWidth + 1.dp.toPx(), bodyHeight * 0.28f),
            size = Size(tipWidth, bodyHeight * 0.44f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )

        if (percent != null && percent > 0) {
            val pad = stroke + 1.2.dp.toPx()
            val maxFillW = (bodyWidth - pad * 2f).coerceAtLeast(0f)
            val fillRatio = (percent.coerceIn(0, 100) / 100f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(pad, pad),
                size = Size(maxFillW * fillRatio, (bodyHeight - pad * 2f).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun SignalBarsIcon(
    bars: Int,
    color: Color,
) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
        val count = 5
        val gap = 1.5.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val barH = size.height * ((i + 1).toFloat() / count.toFloat())
            val active = (i + 1) <= bars
            drawRoundRect(
                color = if (active) color else Color(0x4494A3B8),
                topLeft = Offset(i * (barWidth + gap), size.height - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CameraGlyphIcon(color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 5.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = color,
            radius = 3.2.dp.toPx(),
            center = Offset(size.width / 2f, size.height / 2f + 0.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
private fun RecordDotGlyphIcon(
    recording: Boolean,
    color: Color,
) {
    Canvas(modifier = Modifier.size(13.dp)) {
        if (recording) {
            drawRoundRect(
                color = color,
                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        } else {
            drawCircle(
                color = color,
                radius = size.minDimension / 2f - 1.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun SettingsGearIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val innerR = size.minDimension * 0.22f
        val ringR = size.minDimension * 0.34f
        val outerR = size.minDimension * 0.46f
        val toothStroke = 2.3.dp.toPx()

        drawCircle(
            color = color,
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )
        for (i in 0 until 8) {
            val angle = (i * 45.0) * (PI / 180.0)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + ringR * cosA, cy + ringR * sinA),
                end = Offset(cx + outerR * cosA, cy + outerR * sinA),
                strokeWidth = toothStroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ConnectionStatusOverlay(
    uiState: BebopPilotUiState,
    onRefreshUsb: () -> Unit,
    onRetryPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = uiState.videoMetrics.state != VideoStreamState.RECEIVING
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Surface(
            color = Color(0xCC0D1219),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .border(1.dp, HudColors.GlassBorder, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val title = when (uiState.overallPhase) {
                    OverallConnectionPhase.USB_DISCONNECTED -> "Conecta el Skycontroller 2"
                    OverallConnectionPhase.USB_PERMISSION_PENDING -> "Permiso USB requerido"
                    OverallConnectionPhase.SEARCHING_BEBOP2,
                    OverallConnectionPhase.MUX_CONNECTED -> "Buscando Bebop 2..."
                    OverallConnectionPhase.BEBOP2_CONNECTED -> "Iniciando vídeo del Bebop 2..."
                    OverallConnectionPhase.ERROR -> "Revisar conexión"
                    else -> "Preparando enlace..."
                }
                Text(
                    text = title,
                    color = HudColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                val detail = when {
                    uiState.errorMessage != null -> uiState.errorMessage
                    uiState.usbState == UsbConnectionState.DISCONNECTED ->
                        "Conecta el cable USB al Skycontroller 2 y enciende el Bebop 2."
                    uiState.usbState == UsbConnectionState.PERMISSION_PENDING ->
                        "Acepta el permiso USB en pantalla para enlazar con el mando."
                    uiState.bebop2State == Bebop2LinkState.CONNECTED ->
                        "Esperando imagen en vivo del dron..."
                    else ->
                        "Enciende el dron y espera a que el indicador del mando quede fijo."
                }
                Text(
                    text = detail,
                    color = if (uiState.errorMessage != null) HudColors.WarnAmber else HudColors.TextSecondary,
                    fontSize = 12.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefreshUsb) {
                        Text("Comprobar USB", color = HudColors.TextPrimary, fontSize = 12.sp)
                    }
                    if (uiState.usbState == UsbConnectionState.PERMISSION_DENIED ||
                        uiState.usbState == UsbConnectionState.PERMISSION_PENDING
                    ) {
                        Button(
                            onClick = onRetryPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent),
                        ) {
                            Text("Permitir USB", color = Color(0xFF082F49), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingAlertsStrip(
    uiState: BebopPilotUiState,
    onDismissDroneNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alertText: String?
    val alertColor: Color
    when {
        uiState.flightSettings.alertState != null -> {
            alertText = "Alerta Bebop 2: ${uiState.flightSettings.alertState}"
            alertColor = HudColors.RecRed
        }
        uiState.flightSettings.magnetoCalibration.required &&
            !uiState.flightSettings.magnetoCalibration.started -> {
            alertText = "Brújula sin calibrar: entra a ⚙ → Vuelo antes de subir alto"
            alertColor = HudColors.WarnAmber
        }
        uiState.droneRecording.isMemoryFull -> {
            alertText = "Memoria del Bebop 2 llena"
            alertColor = HudColors.RecRed
        }
        uiState.droneRecording.errorText != null -> {
            alertText = uiState.droneRecording.errorText
            alertColor = HudColors.RecRed
        }
        uiState.localRecording.errorText != null -> {
            alertText = uiState.localRecording.errorText
            alertColor = HudColors.WarnAmber
        }
        uiState.photoStatus.errorText != null -> {
            alertText = uiState.photoStatus.errorText
            alertColor = HudColors.WarnAmber
        }
        uiState.flightSettings.flatTrimNotice != null -> {
            alertText = uiState.flightSettings.flatTrimNotice
            alertColor = HudColors.OkGreen
        }
        uiState.droneRecording.lastSavedNotice != null -> {
            alertText = uiState.droneRecording.lastSavedNotice
            alertColor = HudColors.OkGreen
        }
        uiState.photoStatus.state == PhotoCaptureState.TAKEN -> {
            alertText = "Foto guardada en el Bebop 2"
            alertColor = HudColors.OkGreen
        }
        uiState.localRecording.lastSavedDisplayPath != null &&
            uiState.localRecording.state == LocalRecordState.IDLE -> {
            alertText = "MP4 guardado: ${uiState.localRecording.lastSavedDisplayPath}"
            alertColor = HudColors.OkGreen
        }
        else -> {
            alertText = null
            alertColor = HudColors.Accent
        }
    }

    AnimatedVisibility(
        visible = alertText != null,
        enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        if (alertText != null) {
            Surface(
                color = Color(0xDC0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(1.dp, alertColor, RoundedCornerShape(12.dp))
                    .clickable { onDismissDroneNotice() },
            ) {
                Text(
                    text = alertText,
                    color = HudColors.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun VerticalDividerPill() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(Color(0x33FFFFFF)),
    )
}
