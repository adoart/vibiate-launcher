package com.vibiate.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.vibiate.launcher.data.LauncherFont
import com.vibiate.launcher.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = AviateCyan,
    onPrimary = DarkBackground,
    secondary = AviateCyan,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceMuted,
    surfaceVariant = DarkSurface,
    outline = Color(0x33FFFFFF),
)

private val LightColors = lightColorScheme(
    primary = AviateTeal,
    onPrimary = LightSurface,
    secondary = AviateTeal,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceMuted,
    surfaceVariant = LightSurface,
    outline = Color(0x33000000),
)

private fun LauncherFont.toFontFamily(): FontFamily = when (this) {
    LauncherFont.SYSTEM -> FontFamily.Default
    LauncherFont.SANS_SERIF -> FontFamily.SansSerif
    LauncherFont.SERIF -> FontFamily.Serif
    LauncherFont.MONOSPACE -> FontFamily.Monospace
}

/** Re-apply the default type scale with [family] swapped into every style. */
private fun typographyWith(family: FontFamily): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.applyFont() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.applyFont(),
        displayMedium = base.displayMedium.applyFont(),
        displaySmall = base.displaySmall.applyFont(),
        headlineLarge = base.headlineLarge.applyFont(),
        headlineMedium = base.headlineMedium.applyFont(),
        headlineSmall = base.headlineSmall.applyFont(),
        titleLarge = base.titleLarge.applyFont(),
        titleMedium = base.titleMedium.applyFont(),
        titleSmall = base.titleSmall.applyFont(),
        bodyLarge = base.bodyLarge.applyFont(),
        bodyMedium = base.bodyMedium.applyFont(),
        bodySmall = base.bodySmall.applyFont(),
        labelLarge = base.labelLarge.applyFont(),
        labelMedium = base.labelMedium.applyFont(),
        labelSmall = base.labelSmall.applyFont(),
    )
}

/** True while the resolved color scheme is the dark one. */
val LocalIsDark = compositionLocalOf { true }

@Composable
fun VibiateTheme(
    mode: ThemeMode,
    font: LauncherFont = LauncherFont.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typographyWith(font.toFontFamily()),
            content = content,
        )
    }
}
