package com.alyaqdhan.mybattery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

class BatteryDetailsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.decorView.post {
            val isDark = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        val viewModel = appViewModel

        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        BatteryDetailsScreen(
                            viewModel = viewModel,
                            onBack    = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryDetailsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val info = viewModel.batteryInfo ?: BatteryInfo(
        healthPercent = null, cycleCount = null, healthUnsupported = false, readSuccess = false
    )

    val tp     = textPrimary()
    val ts     = textSecondary()
    val muted  = textMuted()
    val border = cardBorderColor()
    val green  = gaugeGreen()

    var backHandled by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (!backHandled) { backHandled = true; onBack() }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "details_fade")

    // Automatically load full dump and precise fields if currently in concise mode
    LaunchedEffect(info.logFileName, info.isConciseOnly) {
        if (info.isConciseOnly || (info.rawBatteryDumpText.isBlank() && info.readSuccess)) {
            viewModel.loadFullDetails()
        }
    }

    var rawSearchQuery by remember { mutableStateOf("") }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(alpha)
    ) {
        // Top App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 48.dp, bottom = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!backHandled) { backHandled = true; onBack() } }) {
                Icon(painterResource(R.drawable.arrow_back), "Back", tint = tp)
            }

            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    "Battery Details",
                    color = tp,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (info.logFileName.isNotEmpty()) {
                    Text(
                        BatteryParser.truncateLogDisplayName(info.logFileName) +
                                if (info.relativeDate.isNotEmpty()) " · " + info.relativeDate else "",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            if (info.rawBatteryDumpText.isNotBlank()) {
                FilledTonalButton(
                    onClick = { copyToClipboard("Dump", info.rawBatteryDumpText) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1C1C20),
                        contentColor = Color.White
                    )
                ) {
                    Text("Copy Dump", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }

        HorizontalDivider(color = border, thickness = 0.5.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section A: Battery Capacity
            CapacitySection(info, border, tp, ts, muted, green)

            // Section B: Charging Information
            ChargingSection(info, border, tp, ts, muted)

            // Section C: Temperature and Electrical Readings
            ElectricalReadingsSection(info, border, tp, ts, muted)

            // Section D: Battery Protection
            ProtectionSection(info, border, tp, ts, muted)

            // Section E: Battery Information & Metadata
            BatteryMetaSection(info, border, tp, ts, muted)

            // Section F: Advanced Information (Raw fields)
            AdvancedSection(
                info = info,
                searchQuery = rawSearchQuery,
                onSearchChange = { rawSearchQuery = it },
                isExpanded = isAdvancedExpanded,
                onToggleExpand = { isAdvancedExpanded = !isAdvancedExpanded },
                onCopy = copyToClipboard,
                onLoadFullDump = { viewModel.loadFullDetails() },
                border = border,
                tp = tp,
                ts = ts,
                muted = muted
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// -----------------------------------------------------------------------------
// Section A: Battery Capacity
// -----------------------------------------------------------------------------
@Composable
private fun CapacitySection(
    info: BatteryInfo,
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color,
    green: Color
) {
    DetailCard(title = "A. BATTERY CAPACITY", border = border, muted = muted) {
        val asocLabel = info.asocPercent?.let { getBatteryHealthLabel(it) } ?: ""
        val asocDisplay = info.asocPercent?.let { if (asocLabel.isNotBlank()) "$it% ($asocLabel)" else "$it%" } ?: info.asocRaw.ifBlank { "--" }
        val asocColor = when {
            info.asocPercent == null -> tp
            info.asocPercent!! >= 80 -> green
            info.asocPercent!! >= 70 -> Color(0xFFFFB300)
            info.asocPercent!! >= 60 -> Color(0xFFFF9100)
            else                     -> Color(0xFFFF5252)
        }
        DetailRow(
            label = "ASOC Health Estimate",
            value = asocDisplay,
            sublabel = "Primary capacity health calculation",
            valueColor = asocColor
        )

        val bsohLabel = info.bsohPercent?.let { getBatteryHealthLabel(it) } ?: ""
        val bsohDisplay = info.bsohPercent?.let { if (bsohLabel.isNotBlank()) "$it% ($bsohLabel)" else "$it%" } ?: info.bsohRaw.ifBlank { "Not reported" }
        val bsohColor = when {
            info.bsohPercent == null -> tp
            info.bsohPercent!! >= 80 -> green
            info.bsohPercent!! >= 70 -> Color(0xFFFFB300)
            info.bsohPercent!! >= 60 -> Color(0xFFFF9100)
            else                     -> Color(0xFFFF5252)
        }
        DetailRow(
            label = "BSOH Health Estimate",
            value = bsohDisplay,
            sublabel = "Secondary hardware health metric",
            valueColor = bsohColor
        )

        val mah = if (info.chargeCounterUah != null) info.chargeCounterUah / 1000 else null
        val chargeCounterText = if (mah != null) "$mah mAh" else "Not reported"
        val chargeCounterSub = if (info.chargeCounterUah != null) {
            "Coulomb counter: ${info.chargeCounterUah} µAh (instantaneous, not nominal capacity)"
        } else {
            "Instantaneous coulomb charge, not total design capacity"
        }
        DetailRow(
            label = "Charge Counter",
            value = chargeCounterText,
            sublabel = chargeCounterSub,
            valueColor = tp
        )

        val cycleText = info.cycleCount?.let { "$it cycles" } ?: "Not reported"
        DetailRow(
            label = "Charge Cycles",
            value = cycleText,
            sublabel = "Full charge equivalent count",
            valueColor = tp
        )

        val firstUseDate = info.batteryDateFormatted.ifBlank { "Not reported" }
        val firstUseSub = if (info.llbType.isNotEmpty()) {
            "Calibration register: LLB ${info.llbType}"
        } else {
            "Extracted from hardware calibration registers"
        }
        DetailRow(
            label = "First-Use / Assembly Date",
            value = firstUseDate,
            sublabel = firstUseSub,
            valueColor = tp,
            isLast = true
        )
    }
}

// -----------------------------------------------------------------------------
// Section B: Charging Information
// -----------------------------------------------------------------------------
@Composable
private fun ChargingSection(
    info: BatteryInfo,
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color
) {
    DetailCard(title = "B. CHARGING INFORMATION", border = border, muted = muted) {
        DetailRow(
            label = "Current Charging State",
            value = info.status.ifBlank { "Not charging" },
            sublabel = "Live system status",
            valueColor = tp
        )

        DetailRow(
            label = "Power Source Detected",
            value = info.powerSourceSummary,
            sublabel = "AC, USB, or Wireless connection status",
            valueColor = tp
        )

        DetailRow(
            label = "Fast Charging",
            value = info.fastCharging.ifBlank { "Standard / Off" },
            sublabel = "Adaptive Fast Charging / SFC profile",
            valueColor = tp
        )

        val maxCurr = info.maxChargingCurrentMa?.let { "$it mA" } ?: "Not reported"
        DetailRow(
            label = "Max Charging Current",
            value = maxCurr,
            sublabel = "Peak input current allowed",
            valueColor = tp
        )

        val maxVolt = info.maxChargingVoltageMv?.let { "$it mV" } ?: "Not reported"
        DetailRow(
            label = "Max Charging Voltage",
            value = maxVolt,
            sublabel = "Peak charger rail voltage",
            valueColor = tp
        )

        val protection = info.protectionThreshold?.let { "$it%" } ?: "None (100%)"
        DetailRow(
            label = "Battery Protection Threshold",
            value = protection,
            sublabel = "Configured charging cap",
            valueColor = tp,
            isLast = true
        )
    }
}

// -----------------------------------------------------------------------------
// Section C: Temperature and Electrical Readings
// -----------------------------------------------------------------------------
@Composable
private fun ElectricalReadingsSection(
    info: BatteryInfo,
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color
) {
    DetailCard(title = "C. TEMPERATURE AND ELECTRICAL READINGS", border = border, muted = muted) {
        // Notice distinguishing live vs historical
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF16161A),
            border = BorderStroke(1.dp, border),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Text(
                "Live readings reflect the moment the log was captured. Peak values reflect lifetime historical maximums recorded by the battery controller.",
                modifier = Modifier.padding(12.dp),
                color = ts,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
            )
        }

        val tempText = info.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--"
        DetailRow(
            label = "Current Temperature",
            value = tempText,
            sublabel = "Internal thermistor sensor",
            valueColor = tp
        )

        val voltText = info.voltageV?.let { String.format(Locale.US, "%.2f V", it) } ?: "--"
        DetailRow(
            label = "Current Voltage",
            value = voltText,
            sublabel = "Cell terminal voltage",
            valueColor = tp
        )

        val currText = info.currentNowMa?.let { (if (it > 0) "+$it" else "$it") + " mA" } ?: "--"
        DetailRow(
            label = "Instantaneous Current",
            value = currText,
            sublabel = "Negative when discharging, positive when charging",
            valueColor = tp
        )

        val maxTempText = info.maxRecordedTempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Not recorded"
        DetailRow(
            label = "Highest Recorded Temperature",
            value = maxTempText,
            sublabel = "mSavedBatteryMaxTemp hardware register",
            valueColor = tp
        )

        val maxCurrText = info.maxRecordedCurrentMa?.let { "$it mA" } ?: "Not recorded"
        DetailRow(
            label = "Highest Recorded Current",
            value = maxCurrText,
            sublabel = "mSavedBatteryMaxCurrent hardware register",
            valueColor = tp,
            isLast = true
        )
    }
}

// -----------------------------------------------------------------------------
// Section D: Battery Protection
// -----------------------------------------------------------------------------
@Composable
private fun ProtectionSection(
    info: BatteryInfo,
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color
) {
    DetailCard(title = "D. BATTERY PROTECTION", border = border, muted = muted) {
        val mode = when {
            info.protectionMode.isNotBlank() -> info.protectionMode
            info.protectionThreshold != null -> "Enabled"
            else -> "Standard (Off)"
        }
        DetailRow(
            label = "Protection Mode",
            value = mode,
            sublabel = "Samsung Protect Battery setting",
            valueColor = tp
        )

        val threshold = info.protectionThreshold?.let { "$it%" } ?: "Not configured"
        DetailRow(
            label = "Protection Threshold",
            value = threshold,
            sublabel = "Maximum charge percentage limit",
            valueColor = tp
        )

        val presence = when (info.present) {
            true -> "Detected"
            false -> "Not detected"
            null -> "Present"
        }
        DetailRow(
            label = "Battery Presence",
            value = presence,
            sublabel = "Hardware sensor presence signal",
            valueColor = tp
        )

        val auth = info.authResult.ifBlank { "Not reported" }
        DetailRow(
            label = "Authentication Result",
            value = auth,
            sublabel = "Diagnostic hardware check; unverified states do not imply battery fault",
            valueColor = tp,
            isLast = true
        )
    }
}

// -----------------------------------------------------------------------------
// Section E: Battery Information
// -----------------------------------------------------------------------------
@Composable
private fun BatteryMetaSection(
    info: BatteryInfo,
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color
) {
    DetailCard(title = "E. BATTERY INFORMATION", border = border, muted = muted) {
        DetailRow(
            label = "Technology",
            value = info.technology.ifBlank { "Li-ion" },
            sublabel = "Cell chemistry specification",
            valueColor = tp
        )

        DetailRow(
            label = "First-Use / Assembly Date",
            value = info.batteryDateFormatted.ifBlank { "Unknown" },
            sublabel = "Calculated from manufacturing register",
            valueColor = tp
        )

        DetailRow(
            label = "Log Diagnostic Timestamp",
            value = info.logTimestampFormatted.ifBlank { "Unavailable" },
            sublabel = "Exact time the sysdump log was generated",
            valueColor = tp
        )

        val displayName = BatteryParser.truncateLogDisplayName(info.logFileName).ifBlank { "Unavailable" }
        DetailRow(
            label = "Dumpstate File Name",
            value = displayName,
            sublabel = info.logFileName,
            valueColor = tp
        )

        DetailRow(
            label = "Log Relative Age",
            value = info.relativeDate.ifBlank { "Unavailable" },
            sublabel = "Time elapsed since log was generated",
            valueColor = tp,
            isLast = true
        )
    }
}

// -----------------------------------------------------------------------------
// Section F: Advanced Information (Raw Battery Fields)
// -----------------------------------------------------------------------------
@Composable
private fun AdvancedSection(
    info: BatteryInfo,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: (String, String) -> Unit,
    onLoadFullDump: () -> Unit = {},
    border: Color,
    tp: Color,
    ts: Color,
    muted: Color
) {
    val rawEntries = info.rawEntries
    val filteredEntries = remember(rawEntries, searchQuery) {
        if (searchQuery.isBlank()) rawEntries
        else {
            val q = searchQuery.lowercase()
            rawEntries.filter { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "F. ADVANCED INFORMATION",
                        color = tp,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (info.isConciseOnly) "Summary mode (raw fields skipped for fast load)"
                        else "${rawEntries.size} raw fields parsed from dumpstate",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF16161A),
                    border = BorderStroke(1.dp, border)
                ) {
                    Text(
                        if (isExpanded) "Hide" else "Show All",
                        modifier = Modifier
                            .clickable(onClick = onToggleExpand)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = tp,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search raw fields...", color = muted, fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3A3A40),
                            unfocusedBorderColor = border,
                            focusedContainerColor = Color(0xFF0F0F12),
                            unfocusedContainerColor = Color(0xFF0F0F12),
                            focusedTextColor = tp,
                            unfocusedTextColor = tp
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Tap any row to copy its value.",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Spacer(Modifier.height(8.dp))

                    if (filteredEntries.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (info.isConciseOnly) "Fast summary active: raw dump skipped for speed. Tap below to fetch complete data."
                                    else if (rawEntries.isEmpty()) "No raw battery fields available."
                                    else "No fields match \"$searchQuery\"",
                                    color = muted,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (info.isConciseOnly) {
                                    Spacer(Modifier.height(12.dp))
                                    Surface(
                                        onClick = onLoadFullDump,
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1E1E24),
                                        border = BorderStroke(1.dp, border)
                                    ) {
                                        Text(
                                            "Load Full Dumpstate",
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            color = tp,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredEntries.forEach { entry ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCopy(entry.key, "${entry.key}: ${entry.value}") },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F0F12),
                                    border = BorderStroke(0.5.dp, border)
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            entry.key,
                                            color = Color(0xFF7A9CC0),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            ),
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                                        )
                                        Text(
                                            entry.value,
                                            color = tp,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            textAlign = TextAlign.End,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Shared Layout Helpers
// -----------------------------------------------------------------------------
@Composable
private fun DetailCard(
    title: String,
    border: Color,
    muted: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                title,
                color = textPrimary(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    sublabel: String = "",
    valueColor: Color = Color.White,
    isLast: Boolean = false
) {
    val border = cardBorderColor()
    val tp = textPrimary()
    val ts = textSecondary()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .padding(end = 16.dp)
            ) {
                Text(
                    label,
                    color = tp,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (sublabel.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sublabel,
                        color = ts,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
                    )
                }
            }

            Text(
                value,
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                softWrap = false
            )
        }

        if (!isLast) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = border, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
        }
    }
}
