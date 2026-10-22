package com.alyaqdhan.mybattery

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

@Composable
fun HealthGaugeCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    arcAnimatable: Animatable<Float, AnimationVector1D>,
    amplitude: Float,
    gaugeReplayKey: Int = 0
) {
    val isUnsupported = info.healthUnsupported
    val targetPct = if (isUnsupported) 100f
    else (info.healthPercent ?: 0).coerceIn(0, 100).toFloat()

    LaunchedEffect(isLoadingDetail, targetPct, gaugeReplayKey) {
        if (isLoadingDetail) {
            arcAnimatable.animateTo(
                targetValue   = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        } else {
            if (arcAnimatable.value >= 0.02f) arcAnimatable.snapTo(0f)
            arcAnimatable.animateTo(
                targetValue   = targetPct / 100f,
                animationSpec = tween(1400, easing = FastOutSlowInEasing)
            )
        }
    }

    val animPct = arcAnimatable.value * 100f

    val healthLabel = when {
        isLoadingDetail  -> "Checking battery"
        isUnsupported    -> "Device unsupported"
        animPct >= 95f   -> "Excellent"
        animPct >= 90f   -> "Great"
        animPct >= 80f   -> "Good"
        animPct >= 70f   -> "Fair"
        else             -> "Poor"
    }

    val gaugeColor = when {
        isLoadingDetail || isUnsupported -> Color(0xFF00E676)
        animPct >= 80f                   -> Color(0xFF00E676) // Green
        animPct >= 70f                   -> Color(0xFFFFB300) // Amber
        else                             -> Color(0xFFFF5252) // Red (Below 70% Poor)
    }

    val muted  = textMuted()
    val border = cardBorderColor()
    val tp     = textPrimary()
    val ts     = textSecondary()

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border    = BorderStroke(1.dp, border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "BATTERY HEALTH",
                    color = tp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                )
                if (!isLoadingDetail && !isUnsupported && info.healthSource == "bsoh") {
                    Text(
                        "BSOH",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(
                                Color(0xFF1E1E22),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = tp,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                FullCircleAnimatedGauge(
                    progress   = if (isUnsupported) 1f else animPct / 100f,
                    isLoading  = isLoadingDetail,
                    trackColor = Color(0xFF1E1E22),
                    arcColor   = gaugeColor,
                    modifier   = Modifier.fillMaxSize()
                )
                Text(
                    if (isLoadingDetail || isUnsupported) "--" else "${animPct.toInt()}%",
                    color = tp,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                )
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = Color.Transparent,
                border = BorderStroke(1.dp, Color(0xFF242428))
            ) {
                Text(
                    healthLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color    = ts,
                    style    = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Full 360-degree circular battery gauge with ongoing continuous animation.
 * Features:
 * - 360 full circular background track
 * - Clockwise green progress arc starting from 12 o'clock (-90 degrees)
 * - Ongoing continuous rotating sweep shimmer
 * - Ongoing traveling energy pulse along the active arc
 * - Subtle breathing glow with zero smudges or tip artifacts
 */
@Composable
fun FullCircleAnimatedGauge(
    progress: Float,
    isLoading: Boolean,
    trackColor: Color,
    arcColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gauge_ongoing")

    // Continuous smooth shimmer rotation along the circle
    val shimmerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_rotation"
    )

    // Continuous traveling energy pulse along the active arc
    val energyPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energy_pulse"
    )

    // Gentle breathing glow
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_glow"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.075f
        val inset       = strokeWidth / 2f
        val arcRect     = Rect(inset, inset, size.width - inset, size.height - inset)
        val centerPoint = Offset(size.width / 2f, size.height / 2f)

        // 1. Full 360 background track
        drawArc(
            color      = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter  = false,
            topLeft    = arcRect.topLeft,
            size       = arcRect.size,
            style      = Stroke(width = strokeWidth)
        )

        if (isLoading) {
            // Spinning loading arc: pure green, no teal
            drawArc(
                color      = arcColor.copy(alpha = breathingAlpha),
                startAngle = shimmerRotation,
                sweepAngle = 100f,
                useCenter  = false,
                topLeft    = arcRect.topLeft,
                size       = arcRect.size,
                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            val clampedProgress = progress.coerceIn(0f, 1f)
            val fullSweep = 360f * clampedProgress

            if (fullSweep > 0f) {
                // Solid arc using pure health color: no sweep gradient, no teal
                drawArc(
                    color      = arcColor.copy(alpha = 0.92f),
                    startAngle = -90f,
                    sweepAngle = fullSweep,
                    useCenter  = false,
                    topLeft    = arcRect.topLeft,
                    size       = arcRect.size,
                    style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Traveling bright highlight dot along the arc: pure white, sin fade
                val pulseSweep = (fullSweep * 0.18f).coerceIn(12f, 40f)
                val pulseStart = -90f + (fullSweep - pulseSweep) * energyPulse
                val pulseFade  = sin(energyPulse * Math.PI.toFloat()).coerceIn(0f, 1f)

                if (pulseFade > 0.05f) {
                    drawArc(
                        color      = Color.White.copy(alpha = 0.50f * pulseFade),
                        startAngle = pulseStart,
                        sweepAngle = pulseSweep,
                        useCenter  = false,
                        topLeft    = arcRect.topLeft,
                        size       = arcRect.size,
                        style      = Stroke(width = strokeWidth * 0.65f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun CycleCountCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    isNewBattery: Boolean = false,
    gaugeReplayKey: Int = 0
) {
    val cycles        = info.cycleCount ?: 0
    val isUnsupported = info.cycleCount == null
    val maxBar        = 5000f

    val cyclesAnimatable = remember { Animatable(0f) }

    LaunchedEffect(gaugeReplayKey, isLoadingDetail, cycles) {
        if (isLoadingDetail) {
            cyclesAnimatable.snapTo(0f)
        } else {
            cyclesAnimatable.snapTo(0f)
            delay(200)
            cyclesAnimatable.animateTo(
                targetValue   = cycles.toFloat(),
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val animatedCycles      = cyclesAnimatable.value
    val animatedBarFraction = (cyclesAnimatable.value / maxBar).coerceIn(0f, 1f)

    val muted   = textMuted()
    val onSurfV = MaterialTheme.colorScheme.onSurfaceVariant
    val border  = cardBorderColor()
    val tp      = textPrimary()

    val statusText = when {
        isLoadingDetail       -> "Loading"
        isNewBattery          -> "No wear"
        isUnsupported         -> "Unavailable"
        animatedCycles < 2000 -> "Low wear"
        animatedCycles < 3000 -> "Moderate wear"
        else                  -> "High wear"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border    = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            // Loop symbol removed: clean typography header
            Column {
                Text(
                    "CHARGE CYCLES",
                    color = tp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isUnsupported && !isLoadingDetail) "Not reported by device"
                    else "Full charge equivalents",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        when {
                            isUnsupported && !isLoadingDetail -> "--"
                            isLoadingDetail                   -> "--"
                            else                              -> animatedCycles.toInt().toString()
                        },
                        color = if (isUnsupported && !isLoadingDetail) muted else tp,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "cycles",
                        color = onSurfV,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!isUnsupported || isLoadingDetail) {
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = Color.Transparent,
                        border = BorderStroke(1.dp, border)
                    ) {
                        Text(
                            statusText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color    = tp,
                            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .background(border, RoundedCornerShape(2.dp))
            ) {
                if (!isUnsupported) {
                    Box(
                        Modifier.fillMaxWidth(animatedBarFraction).fillMaxHeight()
                            .background(tp, RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0",     color = muted, style = MaterialTheme.typography.labelSmall)
                Text("2000",  color = if (!isUnsupported && animatedCycles >= 2000) onSurfV else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("3000",  color = if (!isUnsupported && animatedCycles >= 3000) onSurfV else muted,
                    style = MaterialTheme.typography.labelSmall)
                Text("5000+", color = if (!isUnsupported && animatedCycles >= 5000) tp      else muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Section 3 -- Quick Battery Information card.
 * Displays level, temperature, and voltage in a clean 3-metric layout.
 */
/**
 * Three distinct metric cards (Charge, Temperature, Voltage) matching the minimalist AMOLED design:
 * Each card features a green SVG icon at top-left, bold white reading in the center, and gray label at bottom.
 */
@Composable
fun QuickBatteryInfoCard(
    info: BatteryInfo,
    isLoading: Boolean = false
) {
    val border  = cardBorderColor()
    val tp      = textPrimary()
    val ts      = textSecondary()

    val levelText = when {
        isLoading -> "--"
        info.level != null -> "${info.level}%"
        else -> "--"
    }

    val tempText = when {
        isLoading -> "--"
        info.temperatureC != null -> String.format(Locale.US, "%.1f°C", info.temperatureC)
        else -> "--"
    }

    val voltText = when {
        isLoading -> "--"
        info.voltageV != null -> String.format(Locale.US, "%.2f V", info.voltageV)
        else -> "--"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickInfoTile(
            modifier = Modifier.weight(1f),
            iconRes  = R.drawable.ic_metric_charge,
            value    = levelText,
            label    = "Charge",
            border   = border,
            tp       = tp,
            ts       = ts
        )
        QuickInfoTile(
            modifier = Modifier.weight(1f),
            iconRes  = R.drawable.ic_metric_temp,
            value    = tempText,
            label    = "Temperature",
            border   = border,
            tp       = tp,
            ts       = ts
        )
        QuickInfoTile(
            modifier = Modifier.weight(1f),
            iconRes  = R.drawable.ic_metric_voltage,
            value    = voltText,
            label    = "Voltage",
            border   = border,
            tp       = tp,
            ts       = ts
        )
    }
}

@Composable
private fun QuickInfoTile(
    modifier: Modifier = Modifier,
    iconRes: Int,
    value: String,
    label: String,
    border: Color,
    tp: Color,
    ts: Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = Color(0xFF101013),
        border   = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                value,
                color = tp,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = ts,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Section 4 -- Full-width button navigating to the dedicated Battery Details screen.
 */
@Composable
fun ViewBatteryDetailsButton(
    isConciseOnly: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF16161A),
            contentColor   = Color.White
        ),
        border    = BorderStroke(1.dp, Color(0xFF282830)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View battery details",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
                if (isConciseOnly) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter            = painterResource(R.drawable.info),
                        contentDescription = "Quick view only",
                        modifier           = Modifier.size(13.dp),
                        tint               = Color(0xFF8E8E93)
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.arrow_forward_ios),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = Color(0xFF8E8E93)
            )
        }
    }
}
