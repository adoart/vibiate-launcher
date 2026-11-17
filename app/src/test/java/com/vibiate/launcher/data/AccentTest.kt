package com.vibiate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {

    // ---- accentFromWallpaper ----

    @Test
    fun saturatedSecondaryBeatsGreyPrimary() {
        val grey = 0xFF777777.toInt()
        val red = 0xFFE53935.toInt()
        val result = accentFromWallpaper(grey, red, null)
        // Result should be reddish, not grey.
        val hsl = argbToHsl(result)
        assertTrue(hsl.s >= 0.40f)
        assertTrue(hsl.h < 15f || hsl.h > 345f)
    }

    @Test
    fun darkNavyKeepsHueAndLightens() {
        val navy = 0xFF0A0A2E.toInt()
        val result = accentFromWallpaper(navy, null, null)
        val hsl = argbToHsl(result)
        assertTrue(hsl.l in 0.37f..0.56f)
        assertTrue(kotlin.math.abs(hsl.h - argbToHsl(navy).h) <= 2f)
    }

    @Test
    fun pastelPinkClampedIntoRange() {
        val pink = 0xFFF8BBD0.toInt()
        val result = accentFromWallpaper(pink, null, null)
        val hsl = argbToHsl(result)
        assertTrue(hsl.l in 0.37f..0.56f)
        assertTrue(kotlin.math.abs(hsl.h - argbToHsl(pink).h) <= 2f)
    }

    @Test
    fun greyscaleWallpaperGivesNeutralGrey() {
        val result = accentFromWallpaper(
            0xFF666666.toInt(), 0xFF999999.toInt(), 0xFF333333.toInt(),
        )
        val hsl = argbToHsl(result)
        assertTrue(hsl.s <= 0.06f)
        assertTrue(hsl.l in 0.39f..0.51f)
    }

    @Test
    fun nullSecondaryTertiaryHandled() {
        val result = accentFromWallpaper(0xFF43A047.toInt(), null, null)
        val hsl = argbToHsl(result)
        assertTrue(hsl.h in 120f..140f)
    }

    // ---- onAccentColor / contrast ----

    @Test
    fun onAccentPicksReadableText() {
        assertEquals(
            0xFF000000.toInt(),
            onAccentColor(0xFFFFEB3B.toInt()),
        )
        assertEquals(
            0xFFFFFFFF.toInt(),
            onAccentColor(0xFF3949AB.toInt()),
        )
    }

    @Test
    fun presetsAndWallpaperResultsHaveContrast() {
        val inputs = ACCENT_PRESETS + listOf(
            accentFromWallpaper(0xFFE53935.toInt(), null, null),
            accentFromWallpaper(0xFF1B5E20.toInt(), 0xFF795548.toInt(), null),
            accentFromWallpaper(0xFFDDDDDD.toInt(), 0xFFEEEEEE.toInt(), null),
            accentFromWallpaper(0xFF0A0A2E.toInt(), null, null),
            accentFromWallpaper(0xFFF8BBD0.toInt(), null, null),
        )
        for (accent in inputs) {
            assertTrue(
                "accent ${Integer.toHexString(accent)}",
                contrastRatio(accent, onAccentColor(accent)) >= 3.0,
            )
        }
    }

    // ---- hue round-trip ----

    @Test
    fun hueAccentRoundTrips() {
        for (h in listOf(0f, 120f, 240f, 359f)) {
            val back = accentHue(hueAccent(h))
            val diff = kotlin.math.abs(back - h)
            assertTrue(
                "hue $h -> $back",
                diff <= 1f || diff >= 359f,
            )
        }
    }
}
