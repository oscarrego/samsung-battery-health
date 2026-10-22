package com.alyaqdhan.mybattery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Battery health circle color: vibrant green
val AccentGreen = Color(0xFF00E676)

// Neutral monochrome palette
val AccentWhite  = Color(0xFFFFFFFF)
val AccentSilver = Color(0xFFCCCCCC)
val AccentDim    = Color(0xFF888888)

// Dark surface palette (AMOLED black and white)
val DarkBgDeep        = Color(0xFF000000)
val DarkBgCard        = Color(0xFF131315) // subtle dark graphite black, zero green tint
val DarkBgCardBorder  = Color(0xFF242428) // clean subtle border
val DarkTextPrimary   = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFF8E8E93)
val DarkTextMuted     = Color(0xFF48484A)

// Light surface palette
val LightBgDeep        = Color(0xFF000000)
val LightBgCard        = Color(0xFF131315)
val LightBgCardBorder  = Color(0xFF242428)
val LightTextPrimary   = Color(0xFFFFFFFF)
val LightTextSecondary = Color(0xFF8E8E93)
val LightTextMuted     = Color(0xFF48484A)

@Composable
fun MyBatteryTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary                 = AccentWhite,
        onPrimary               = Color.Black,
        primaryContainer        = Color(0xFF1C1C1E),
        onPrimaryContainer      = Color.White,
        secondary               = Color(0xFFE5E5EA),
        onSecondary             = Color.Black,
        secondaryContainer      = Color(0xFF1C1C1E),
        onSecondaryContainer    = Color.White,
        tertiary                = Color(0xFFAAAAAA),
        onTertiary              = Color.Black,
        tertiaryContainer       = Color(0xFF1C1C1E),
        onTertiaryContainer     = Color.White,
        error                   = Color(0xFFFF453A),
        onError                 = Color.Black,
        errorContainer          = Color(0xFF2C1010),
        onErrorContainer        = Color(0xFFFFD2D0),
        background              = DarkBgDeep,
        onBackground            = DarkTextPrimary,
        surface                 = DarkBgCard,
        onSurface               = DarkTextPrimary,
        surfaceVariant          = Color(0xFF18181A),
        onSurfaceVariant        = DarkTextSecondary,
        outline                 = DarkBgCardBorder,
        outlineVariant          = DarkTextMuted,
        scrim                   = Color(0xFF000000),
        inverseSurface          = DarkTextPrimary,
        inverseOnSurface        = DarkBgDeep,
        inversePrimary          = Color.Black,
        surfaceTint             = Color.Transparent,
        surfaceContainerHighest = Color(0xFF1E1E22),
        surfaceContainerHigh    = Color(0xFF18181A),
        surfaceContainer        = Color(0xFF131315),
        surfaceContainerLow     = Color(0xFF0C0C0E),
        surfaceContainerLowest  = DarkBgDeep,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Theme-aware color helpers
@Composable fun cardBorderColor()      = MaterialTheme.colorScheme.outline
@Composable fun textPrimary()          = MaterialTheme.colorScheme.onBackground
@Composable fun textSecondary()        = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun textMuted()            = MaterialTheme.colorScheme.outlineVariant
@Composable fun accentGreenEffective() = MaterialTheme.colorScheme.primary

// Battery health circle is green; all other gauges are monochrome
@Composable fun gaugeGreen()  = AccentGreen
@Composable fun gaugeOrange() = AccentWhite
@Composable fun gaugeRed()    = AccentSilver
@Composable fun gaugeBlue()   = AccentDim
@Composable fun gaugeGray()   = MaterialTheme.colorScheme.outlineVariant
