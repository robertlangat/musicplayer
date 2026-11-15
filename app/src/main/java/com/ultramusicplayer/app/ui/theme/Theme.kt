package com.ultramusicplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ultramusicplayer.app.data.ThemeMode

val Charcoal = Color(0xFF1C1D21)
val CharcoalSurface = Color(0xFF25272D)
val CharcoalHigh = Color(0xFF2F3239)

val AccentChoices = listOf(
    0xFF5EC8F2, 0xFFFF6B6B, 0xFFFFB347, 0xFF7ED957, 0xFFB388FF,
    0xFFFF80AB, 0xFF4DD0E1, 0xFFFFD54F, 0xFF90A4AE, 0xFF26A69A,
)

private fun onColorFor(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

private val UltraTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    )
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun UltraTheme(mode: ThemeMode, accent: Color, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onColorFor(accent),
            primaryContainer = accent.copy(alpha = 0.25f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = onColorFor(accent),
            secondaryContainer = CharcoalHigh,
            onSecondaryContainer = Color.White,
            tertiary = accent,
            background = Charcoal,
            onBackground = Color(0xFFECEDEF),
            surface = CharcoalSurface,
            onSurface = Color(0xFFECEDEF),
            surfaceVariant = CharcoalHigh,
            onSurfaceVariant = Color(0xFFB8BBC2),
            surfaceContainer = CharcoalSurface,
            surfaceContainerHigh = CharcoalHigh,
            surfaceContainerHighest = Color(0xFF383B43),
            outline = Color(0xFF5A5E68),
        )
    } else {
        val deep = Color(
            red = accent.red * 0.7f,
            green = accent.green * 0.7f,
            blue = accent.blue * 0.7f,
        )
        lightColorScheme(
            primary = deep,
            onPrimary = onColorFor(deep),
            primaryContainer = accent.copy(alpha = 0.25f),
            secondary = deep,
            secondaryContainer = Color(0xFFE6E8EC),
            background = Color(0xFFF6F7F9),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9EBEF),
            onSurfaceVariant = Color(0xFF4A4E57),
            surfaceContainer = Color(0xFFF0F1F4),
            surfaceContainerHigh = Color(0xFFE9EBEF),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = UltraTypography, content = content)
}
