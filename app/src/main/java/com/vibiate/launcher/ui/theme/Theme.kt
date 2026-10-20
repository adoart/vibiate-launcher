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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vibiate.launcher.R
import com.vibiate.launcher.data.LauncherFont
import com.vibiate.launcher.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = AviateBlue,
    onPrimary = Color.White,
    secondary = AviateBlue,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceMuted,
    surfaceVariant = DarkSurface,
    outline = Color(0x33FFFFFF),
)

private val LightColors = lightColorScheme(
    primary = AviateBlue,
    onPrimary = LightSurface,
    secondary = AviateBlue,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceMuted,
    surfaceVariant = LightSurface,
    outline = Color(0x33000000),
)

/** Gotham substitute: bundled Montserrat (OFL). Weights below Light resolve to Light. */
private val MontserratFamily = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Thin),
    Font(R.font.montserrat_light, FontWeight.ExtraLight),
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

private fun LauncherFont.toFontFamily(): FontFamily = when (this) {
    LauncherFont.AVIATE -> MontserratFamily
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

/** Translucent overlay palette for the resolved theme. */
val LocalAviateColors = compositionLocalOf { AviateDarkOverlay }

@Composable
fun VibiateTheme(
    mode: ThemeMode,
    font: LauncherFont = LauncherFont.AVIATE,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalAviateColors provides if (dark) AviateDarkOverlay else AviateLightOverlay,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typographyWith(font.toFontFamily()),
            content = content,
        )
    }
}
