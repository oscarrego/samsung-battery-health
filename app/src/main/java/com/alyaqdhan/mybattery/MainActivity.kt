package com.alyaqdhan.mybattery

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // Status-bar / nav-bar appearance tracks theme
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

        // First-run: no folder URI saved → open Setup, finish this Activity
        if (savedInstanceState == null && !viewModel.initialize()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Attach Compose content to the ComposeView declared in activity_main.xml
        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        BatteryDashboard(
                            viewModel         = viewModel,
                            onNavigateSetup   = {
                                startActivity(Intent(this@MainActivity, SetupActivity::class.java)
                                    .putExtra(SetupActivity.EXTRA_SHOW_BACK, true))
                            },
                            onNavigateRaw     = {
                                startActivity(Intent(this@MainActivity, LogRawActivity::class.java))
                            },
                            onNavigateDetails = {
                                startActivity(Intent(this@MainActivity, BatteryDetailsActivity::class.java))
                            },
                            onRunDumpstate    = {
                                // Launch Samsung SysDump via the dialer: one tap opens the menu
                                val dialUri = android.net.Uri.fromParts("tel", viewModel.dialCode, null)
                                startActivity(Intent(Intent.ACTION_DIAL, dialUri))
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appViewModel.checkOnResume()
    }
}

// ── BatteryDashboard composable ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryDashboard(
    viewModel: AppViewModel,
    onNavigateSetup: () -> Unit,
    onNavigateRaw: () -> Unit,
    onNavigateDetails: () -> Unit,
    onRunDumpstate: () -> Unit
) {
    val info             = viewModel.batteryInfo
    val isLoadingDetail  = viewModel.isLoadingDetail || info == null
    val isRefreshing     = viewModel.isRefreshing
    val folderAccessible = viewModel.alreadyHasPerm && viewModel.folderAccessible
    val showLogSheet     = viewModel.showLogSheet
    val allLogEntries    = viewModel.allLogEntries
    val gaugeAmplitude   = viewModel.gaugeAmplitude
    val gaugeReplayKey   = viewModel.gaugeReplayKey

    val arcAnimatable = remember { Animatable(0f) }

    LaunchedEffect(isLoadingDetail) {
        if (isLoadingDetail) viewModel.startGaugePulse()
        else viewModel.stopGaugePulse()
    }

    val alreadyHasData = remember { info?.readSuccess == true }
    var shown by remember { mutableStateOf(alreadyHasData) }
    LaunchedEffect(Unit) { shown = true }

    val headerAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "ha")
    val headerSlide by animateFloatAsState(
        if (shown) 0f else -18f, tween(400, easing = FastOutSlowInEasing), label = "hs")
    val gaugeAlpha  by animateFloatAsState(
        if (shown) 1f else 0f, tween(450, delayMillis = 140), label = "ga")
    val gaugeSlide  by animateFloatAsState(
        if (shown) 0f else 24f, tween(450, easing = FastOutSlowInEasing, delayMillis = 140), label = "gs")
    val cardAlpha   by animateFloatAsState(
        if (shown) 1f else 0f, tween(400, delayMillis = 280), label = "ca")

    val effectiveInfo = info ?: BatteryInfo(
        healthPercent = null, cycleCount = null, healthUnsupported = false, readSuccess = false
    )

    val tp    = textPrimary()
    val muted = textMuted()

    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RescanPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRescan     = { viewModel.rescan() },
            modifier     = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(52.dp))

                // ── Header: title + metadata only (buttons moved to bottom nav) ─
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(headerAlpha)
                        .offset(y = headerSlide.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Battery Health", color = tp,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    if (isLoadingDetail) {
                        Text("…", color = muted.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall)
                        Text("…", color = muted.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Text("…", color = muted.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.labelSmall)
                    } else {
                        if (effectiveInfo.logFileName.isNotEmpty()) {
                            val displayName = BatteryParser.truncateLogDisplayName(effectiveInfo.logFileName)
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    displayName, color = muted, textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (!folderAccessible) {
                                    Spacer(Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            "cached",
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                            color    = muted.copy(alpha = 0.7f),
                                            style    = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                letterSpacing = 0.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (effectiveInfo.relativeDate.isNotEmpty())
                            Text(
                                "Log created ${effectiveInfo.relativeDate}",
                                color = muted, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall
                            )
                        if (effectiveInfo.batteryDateFormatted.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Installed on ${effectiveInfo.batteryDateFormatted}",
                                color = muted.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                val isNewBattery = !isLoadingDetail && !effectiveInfo.healthUnsupported &&
                        (effectiveInfo.healthPercent ?: 0) >= 100

                Box(Modifier.alpha(gaugeAlpha).offset(y = gaugeSlide.dp)) {
                    HealthGaugeCard(effectiveInfo, isLoadingDetail, arcAnimatable, gaugeAmplitude, gaugeReplayKey)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.alpha(cardAlpha)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        QuickBatteryInfoCard(effectiveInfo, isLoadingDetail)
                        Spacer(Modifier.height(16.dp))
                        CycleCountCard(effectiveInfo, isLoadingDetail, isNewBattery, gaugeReplayKey)
                        Spacer(Modifier.height(16.dp))
                        ViewBatteryDetailsButton(
                            isConciseOnly = effectiveInfo.isConciseOnly,
                            onClick = onNavigateDetails
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            color = muted.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                // Extra bottom padding so content clears the bottom nav bar
                Spacer(Modifier.height(96.dp))
            }
        }

        // ── Bottom navigation bar ──────────────────────────────────────────────
        BottomNavBar(
            modifier            = Modifier.align(Alignment.BottomCenter),
            isLoadingDetail     = isLoadingDetail,
            onOpenLogs          = { if (!isLoadingDetail) viewModel.openLogSheet() },
            onRunDiagnostics    = onRunDumpstate,
            onOpenGuide         = { if (!isLoadingDetail) onNavigateSetup() }
        )

        // Interaction-blocking overlay while loading detail, with skip controls on top
        if (isLoadingDetail) {
            // 1. Scrim layer that prevents interaction with dashboard
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick           = {},
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )

            // 2. One-Minute Center Dialog with Wait and Skip options
            androidx.compose.animation.AnimatedVisibility(
                visible  = viewModel.showOneMinuteDialog,
                enter    = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.92f),
                exit     = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.92f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape   = RoundedCornerShape(22.dp),
                        color   = Color(0xFF141418),
                        border  = BorderStroke(1.dp, Color(0xFF2C2C34)),
                        shadowElevation = 24.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp)
                            .clickable(enabled = false, onClick = {})
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Taking too long for logs to load?",
                                color = tp,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Reading this dumpstate log has taken over 1 minute. Would you like to wait for the complete dump, or skip to view the core battery health now?",
                                color = textSecondary(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.continueWaiting() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E1E24),
                                        contentColor   = tp
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF33333E))
                                ) {
                                    Text(
                                        "Wait",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                                Button(
                                    onClick = { viewModel.skipToConciseMode() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00E676),
                                        contentColor   = Color.Black
                                    )
                                ) {
                                    Text(
                                        "Skip",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Log sheet scrim (dimmed backdrop)
        if (showLogSheet && !isLoadingDetail) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        onClick           = { viewModel.dismissLogSheet() },
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )
        }

        // ── Log sheet opening from bottom ─────────────────────────────────────
        AnimatedVisibility(
            visible  = showLogSheet && !isLoadingDetail,
            enter    = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(200)),
            exit     = slideOutVertically(tween(260, easing = FastOutLinearInEasing)) { it } +
                    fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LogSheet(
                allLogEntries   = allLogEntries,
                activeFileName  = effectiveInfo.logFileName,
                onDismiss       = { viewModel.dismissLogSheet() },
                onSelectEntry   = { viewModel.selectLogEntry(it) },
                onReadRaw       = { entry ->
                    viewModel.openRawLog(entry)
                    onNavigateRaw()
                },
                extractTs       = { BatteryParser.extractTimestampFromFileName(it) }
            )
        }
    }
}

// ── Bottom navigation bar ──────────────────────────────────────────────────────

@Composable
private fun BottomNavBar(
    modifier: Modifier = Modifier,
    isLoadingDetail: Boolean,
    onOpenLogs: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = RectangleShape,
        color    = Color.Black
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Left: Logs (hamburger icon)
            BottomNavIconButton(
                icon               = R.drawable.ic_menu,
                contentDescription = "Browse Logs",
                enabled            = !isLoadingDetail,
                onClick            = onOpenLogs,
                modifier           = Modifier.weight(1f)
            )

            // Center: Run Diagnostics (play button)
            BottomNavIconButton(
                icon               = R.drawable.ic_play,
                contentDescription = "Run Diagnostics",
                enabled            = !isLoadingDetail,
                onClick            = onRunDiagnostics,
                modifier           = Modifier.weight(1f)
            )

            // Right: Guide / Info (circular i icon)
            BottomNavIconButton(
                icon               = R.drawable.ic_info_circle,
                contentDescription = "Setup Guide",
                enabled            = !isLoadingDetail,
                onClick            = onOpenGuide,
                modifier           = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavIconButton(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconTint = if (enabled) Color.White else Color(0xFF48484A)

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick  = onClick,
            enabled  = enabled,
            shape    = CircleShape,
            color    = Color.Transparent,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter            = painterResource(icon),
                    contentDescription = contentDescription,
                    tint               = iconTint,
                    modifier           = Modifier.size(30.dp)
                )
            }
        }
    }
}

// ── Log sheet ─────────────────────────────────────────────────────────────────

@Composable
private fun LogSheet(
    allLogEntries: List<DocEntry>,
    activeFileName: String,
    onDismiss: () -> Unit,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit,
    extractTs: (String) -> Long
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .clickable(onClick = {}, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        color           = Color.Black,
        border          = BorderStroke(1.dp, Color(0xFF1A1A1A)),
        shadowElevation = 16.dp,
        shape           = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Drag handle at top with vertical drag gesture to move menu down according to finger movement
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY.value > 120f) {
                                    onDismiss()
                                } else {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val nextOffset = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                coroutineScope.launch {
                                    dragOffsetY.snapTo(nextOffset)
                                }
                            }
                        )
                    }
                    .padding(top = 12.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .background(Color(0xFF383838), RoundedCornerShape(2.dp))
                )
            }

            // Header title (no indication number badge, no X close button)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Log Files",
                    color = textPrimary(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color    = Color(0xFF1A1A1A)
            )

            val logsScrollState = rememberScrollState()
            val logsScope       = rememberCoroutineScope()

            Box(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(logsScrollState)
                        .padding(end = 12.dp, top = 6.dp, bottom = 16.dp)
                ) {
                    if (allLogEntries.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No log files found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        allLogEntries.forEachIndexed { idx, entry ->
                            LogEntry(
                                entry         = entry,
                                isActive      = entry.name == activeFileName,
                                relTime       = relativeTimeFrom(
                                    extractTs(entry.name).takeIf { it != 0L } ?: entry.lastModified
                                ),
                                onSelectEntry = onSelectEntry,
                                onReadRaw     = onReadRaw
                            )
                            if (idx < allLogEntries.lastIndex) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                if (logsScrollState.maxValue > 0) {
                    val trackHeight    = 260.dp
                    val thumbMinHeight = 32.dp
                    val density        = LocalDensity.current
                    val trackPx        = with(density) { trackHeight.toPx() }
                    val thumbMinPx     = with(density) { thumbMinHeight.toPx() }
                    val fraction       = (logsScrollState.value.toFloat() / logsScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                    val thumbPx        = (trackPx * 0.35f).coerceAtLeast(thumbMinPx)
                    val thumbTopPx     = fraction * (trackPx - thumbPx)
                    val isDark         = isSystemInDarkTheme()
                    val thumbColor     = Color(0xFF444444)
                    val trackColor     = Color(0xFF1A1A1A)

                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .width(4.dp)
                            .height(trackHeight)
                            .background(trackColor, RoundedCornerShape(2.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val draggableRange = trackPx - thumbPx
                                    if (draggableRange > 0f) {
                                        val delta = (dragAmount.y / draggableRange) * logsScrollState.maxValue.toFloat()
                                        logsScope.launch { logsScrollState.scrollBy(delta) }
                                    }
                                }
                            }
                    ) {
                        Box(
                            Modifier
                                .offset(y = with(density) { thumbTopPx.toDp() })
                                .width(4.dp)
                                .height(with(density) { thumbPx.toDp() })
                                .background(thumbColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntry(
    entry: DocEntry,
    isActive: Boolean,
    relTime: String,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit
) {
    val displayName = BatteryParser.truncateLogDisplayName(entry.name)
    val subtitleText = when {
        isActive && relTime.isNotEmpty() -> "$relTime · Current Log"
        isActive                         -> "Current Log"
        else                             -> relTime
    }

    Surface(
        onClick  = { if (!isActive) onSelectEntry(entry) },
        enabled  = !isActive,
        shape    = RoundedCornerShape(12.dp),
        color    = if (isActive) Color(0xFF141417) else Color.Transparent,
        border   = if (isActive) BorderStroke(1.dp, Color(0xFF24242A)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitleText.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitleText,
                        color = if (isActive) Color(0xFF9E9EA4) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                onClick  = { onReadRaw(entry) },
                modifier = Modifier.size(36.dp),
                shape    = RoundedCornerShape(8.dp),
                color    = Color(0xFF161619),
                border   = BorderStroke(0.5.dp, Color(0xFF24242A))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.arrow_forward_ios),
                        contentDescription = "View raw",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// ── Relative time helper ──────────────────────────────────────────────────────

private fun relativeTimeFrom(tsMs: Long): String {
    if (tsMs == 0L) return ""
    val diff   = System.currentTimeMillis() - tsMs
    val mins   = diff / 60_000
    val hours  = diff / 3_600_000
    val days   = diff / 86_400_000
    val months = days / 30
    val years  = days / 365
    return when {
        mins  < 1   -> "just now"
        mins  < 60  -> "$mins min${if (mins == 1L) "" else "s"} ago"
        hours < 24  -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        days  < 30  -> "$days day${if (days == 1L) "" else "s"} ago"
        years < 1   -> "$months month${if (months == 1L) "" else "s"} ago"
        else        -> "$years year${if (years == 1L) "" else "s"} ago"
    }
}