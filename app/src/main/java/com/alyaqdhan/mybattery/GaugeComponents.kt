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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

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
        animPct >= 95f   -> "Excellent condition"
        animPct >= 90f   -> "Great condition"
        animPct >= 80f   -> "Good condition"
        animPct >= 70f   -> "Fair condition"
        else             -> "Poor condition"
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
            // BSOH badge - only when applicable
            if (!isLoadingDetail && !isUnsupported && info.healthSource == "bsoh") {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "BSOH",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(Color(0xFF1E1E22), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = tp,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Large percentage text
            Text(
                if (isLoadingDetail || isUnsupported) "--" else "${animPct.toInt()}%",
                color = tp,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
            )

            Spacer(Modifier.height(14.dp))

            // Health condition pill
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

            Spacer(Modifier.height(22.dp))

            // Liquid capsule gauge
            CapsuleBatteryGauge(
                progress   = if (isUnsupported) 1f else animPct / 100f,
                isLoading  = isLoadingDetail,
                trackColor = Color(0xFF1A1A1E),
                fillColor  = gaugeColor,
                modifier   = Modifier.fillMaxWidth().height(56.dp)
            )
        }
    }
}

/**
 * Liquid capsule battery gauge.
 * Fill: red -> orange -> yellow -> green spectrum gradient (left to right).
 * Right edge: single smooth S-curve that sways like a liquid meniscus.
 * 3D volumetric glow, dot grid on unfilled area, shimmer sweep.
 */
@Composable
fun CapsuleBatteryGauge(
    progress: Float,
    isLoading: Boolean,
    trackColor: Color,
    fillColor: Color,   // used for outer glow colour only
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "liq")

    // Wave phase: drives the S-curve side to side (sloshing)
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue  = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wave"
    )

    // Outer glow pulse
    val glowAlpha by inf.animateFloat(
        initialValue = 0.06f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    // Shimmer sweep across fill
    val shimmer by inf.animateFloat(
        initialValue = -0.35f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shim"
    )

    // Loading bounce
    val loadProg by inf.animateFloat(
        initialValue = 0.05f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "load"
    )

    // Spectrum colour stops: red -> orange -> yellow -> green
    val specRed    = Color(0xFFFF3B30)
    val specOrange = Color(0xFFFF8C00)
    val specYellow = Color(0xFFFFD60A)
    val specGreen  = Color(0xFF30D158)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val pad  = h * 0.10f
        val cL   = pad;  val cT  = pad
        val cW   = w - pad * 2f; val cH = h - pad * 2f
        val cR   = cH / 2f
        val capCR = CornerRadius(cR, cR)

        val fillFrac = (if (isLoading) loadProg else progress).coerceIn(0f, 1f)
        val fw = cW * fillFrac

        // -- 1. Outer glow --
        if (fillFrac > 0.02f) {
            val ge = h * 0.12f
            drawRoundRect(
                color        = fillColor.copy(alpha = glowAlpha),
                topLeft      = Offset(cL - ge, cT - ge),
                size         = Size(cW + ge * 2f, cH + ge * 2f),
                cornerRadius = CornerRadius(cR + ge, cR + ge)
            )
        }

        // -- 2. Dark track --
        drawRoundRect(
            color        = trackColor,
            topLeft      = Offset(cL, cT),
            size         = Size(cW, cH),
            cornerRadius = capCR
        )

        // -- 3. Dot grid on ENTIRE track (subtle) --
        val dotSp = cH * 0.195f; val dotR = cH * 0.019f
        var dx = cL + dotSp
        while (dx < cL + cW - dotR) {
            var dy = cT + dotSp
            while (dy < cT + cH - dotR) {
                val rx = dx - cL; val ry = dy - cT
                val minD = min(min(rx, cW - rx), min(ry, cH - ry))
                if (minD > cR * 0.12f || (rx > cR && cW - rx > cR)) {
                    drawCircle(
                        color  = Color(0xFF444448).copy(alpha = 0.45f),
                        radius = dotR,
                        center = Offset(dx, dy)
                    )
                }
                dy += dotSp
            }
            dx += dotSp
        }

        // -- 4. Liquid fill with S-curve right edge --
        if (fw > 1f) {

            // Build the fill path:
            //   top-left -> straight across top to fw -> S-curve wave DOWN the right edge
            //   -> straight back along bottom -> close
            val waveAmp = cH * 0.16f   // subtle but visible liquid S-curve
            val steps   = 80

            val fillPath = Path().apply {
                moveTo(cL, cT)
                lineTo(cL + fw, cT)            // top edge to fill boundary

                // Single S-curve sine wave downward on the right edge
                // sin(t*2PI + phase) gives one full S-cycle top-to-bottom
                for (i in 0..steps) {
                    val t  = i.toFloat() / steps
                    val yp = cT + t * cH
                    val xOff = sin(t * 2f * PI.toFloat() + wavePhase) * waveAmp
                    lineTo(cL + fw + xOff, yp)
                }

                lineTo(cL, cT + cH)            // bottom-left
                close()
            }

            // Capsule clip path
            val clipPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = cL, top = cT,
                        right = cL + cW, bottom = cT + cH,
                        cornerRadius = capCR
                    )
                )
            }

            clipPath(clipPath) {

                // Bright spectrum gradient fill
                drawPath(
                    path  = fillPath,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to specRed,
                            0.33f to specOrange,
                            0.62f to specYellow,
                            1.00f to specGreen
                        ),
                        startX = cL,
                        endX   = cL + cW
                    )
                )

                // 3D glass highlight - top half sheen
                drawPath(
                    path  = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f)
                        ),
                        startY = cT,
                        endY   = cT + cH
                    )
                )

                // Volumetric inner glow - radial bright center
                val glowCx = cL + fw * 0.55f
                val glowCy = cT + cH * 0.50f
                val glowR  = fw * 0.70f
                if (glowR > 1f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(glowCx, glowCy),
                            radius = glowR
                        ),
                        center = Offset(glowCx, glowCy),
                        radius = glowR
                    )
                }

                // Shimmer band
                val shimW = cW * 0.20f
                val shimX = cL + (cW + shimW) * shimmer - shimW
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        startX = shimX, endX = shimX + shimW
                    ),
                    topLeft      = Offset(cL, cT),
                    size         = Size(cW, cH),
                    cornerRadius = capCR
                )
            }

            // Bright rim glow at the wave boundary (soft radial, no hard line)
            if (fillFrac in 0.04f..0.96f) {
                val ex = cL + fw
                val eR = cH * 0.70f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.40f),
                            fillColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(ex, cT + cH / 2f),
                        radius = eR
                    ),
                    center = Offset(ex, cT + cH / 2f),
                    radius = eR
                )
            }
        }

        // -- 5. Capsule border --
        drawRoundRect(
            color        = if (fillFrac > 0.02f) fillColor.copy(alpha = 0.28f)
                           else Color(0xFF2A2A30),
            topLeft      = Offset(cL, cT),
            size         = Size(cW, cH),
            cornerRadius = capCR,
            style        = Stroke(width = cH * 0.022f)
        )
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
        info.temperatureC != null -> String.format(Locale.US, "%.1fÂ°C", info.temperatureC)
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
        color    = MaterialTheme.colorScheme.surface,
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
    val border = cardBorderColor()
    Button(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor   = Color.White
        ),
        border    = BorderStroke(1.dp, border),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View more details",
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
