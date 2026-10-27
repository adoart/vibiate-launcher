package com.vibiate.launcher.ui.theme

import androidx.compose.ui.graphics.Color

// Aviate-inspired palette
val AviateBlue = Color(0xFF33B5E5) // Aviate holoBlue accent
val AviateOrange = Color(0xFFEF5742) // Aviate holoOrange accent

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnSurface = Color(0xFFECECEC)
val DarkOnSurfaceMuted = Color(0xFF9E9E9E)

val LightBackground = Color(0xFFF2F2F2)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF212121)
val LightOnSurfaceMuted = Color(0xFF757575)

/** Translucent overlay colors drawn over the wallpaper, per theme. */
data class AviateColors(
    /** Translucent card background on Collections. */
    val cardBackground: Color,
    /** Page backdrop over the wallpaper on Collections/A–Z. */
    val pageSkrim: Color,
    /** Stronger page backdrop used when cross-window blur is unavailable. */
    val pageSkrimStrong: Color,
    /** Card header title text. */
    val cardTitle: Color,
    /** App labels under icons. */
    val cardLabel: Color,
    /** Secondary/icon tint on cards (glyph, chevron). */
    val cardIcon: Color,
    /** Thin separators on cards, A–Z groups and the rail. */
    val divider: Color,
    /** Primary text over the wallpaper on Home (clock, temperature). */
    val homeText: Color,
    /** Secondary text over the wallpaper on Home (date, condition, hint). */
    val homeTextSecondary: Color,
)

val AviateDarkOverlay = AviateColors(
    cardBackground = Color(0x4C000000),
    pageSkrim = Color(0x66262626),
    pageSkrimStrong = Color(0x99262626),
    cardTitle = Color(0xD9FFFFFF),
    cardLabel = Color(0xD9FFFFFF),
    cardIcon = Color(0xAAFFFFFF),
    divider = Color(0x33FFFFFF),
    homeText = Color(0xFFFFFFFF),
    homeTextSecondary = Color(0xD9FFFFFF),
)

val AviateLightOverlay = AviateColors(
    cardBackground = Color(0x80FFFFFF),
    pageSkrim = Color(0x80F6F5F4),
    pageSkrimStrong = Color(0xB3F6F5F4),
    cardTitle = Color(0xFF999999),
    cardLabel = Color(0xFF333333),
    cardIcon = Color(0xFF999999),
    divider = Color(0x1F000000),
    homeText = Color(0xFF333333),
    homeTextSecondary = Color(0xFF666666),
)
