package com.vibiate.launcher.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Where the launcher accent colour comes from. */
enum class AccentSource { COLOR, WALLPAPER }

const val DEFAULT_ACCENT: Int = 0xFF33B5E5.toInt()

/** Preset swatches shown in the accent picker (ARGB). */
val ACCENT_PRESETS: List<Int> = listOf(
    0xFF33B5E5, // Aviate blue
    0xFFEF5742, // Aviate orange
    0xFF009688, // Teal
    0xFF43A047, // Green
    0xFF7CB342, // Lime
    0xFFFFA000, // Amber
    0xFFF4511E, // Deep orange
    0xFFE53935, // Red
    0xFFD81B60, // Pink
    0xFF8E24AA, // Purple
    0xFF3949AB, // Indigo
    0xFF546E7A, // Blue grey
).map { it.toInt() }

internal data class Hsl(val h: Float, val s: Float, val l: Float)

internal fun argbToHsl(c: Int): Hsl {
    val r = ((c shr 16) and 0xFF) / 255f
    val g = ((c shr 8) and 0xFF) / 255f
    val b = (c and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return Hsl(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Hsl(h, s, l)
}

internal fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

/**
 * Pick an accent from the system-reported wallpaper colours: the most
 * saturated mid-luminance candidate wins; near-greyscale wallpapers give a
 * neutral grey; the result is normalised so it reads well as a top-bar and
 * control accent in both themes.
 */
fun accentFromWallpaper(primary: Int, secondary: Int?, tertiary: Int?): Int {
    var bestHsl = argbToHsl(primary)
    var bestScore = Float.NEGATIVE_INFINITY
    for (c in listOfNotNull(primary, secondary, tertiary)) {
        val hsl = argbToHsl(c)
        val score = hsl.s *
            (1f - abs(hsl.l - 0.5f) * 1.2f).coerceAtLeast(0.1f)
        if (score > bestScore) {
            bestScore = score
            bestHsl = hsl
        }
    }
    return if (bestHsl.s < 0.12f) {
        hslToArgb(0f, 0.05f, bestHsl.l.coerceIn(0.40f, 0.50f))
    } else {
        hslToArgb(
            bestHsl.h,
            bestHsl.s.coerceIn(0.40f, 0.85f),
            bestHsl.l.coerceIn(0.38f, 0.55f),
        )
    }
}

/** Saturated accent at a given hue (HSL s=0.65, l=0.48). */
fun hueAccent(hue: Float): Int = hslToArgb(hue, 0.65f, 0.48f)

/** Hue of an ARGB colour, 0..360. */
fun accentHue(c: Int): Float = argbToHsl(c).h

private fun relLuminance(c: Int): Double {
    fun ch(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * ch((c shr 16) and 0xFF) +
        0.7152 * ch((c shr 8) and 0xFF) +
        0.0722 * ch(c and 0xFF)
}

/** WCAG contrast ratio between two ARGB colours, 1..21. */
fun contrastRatio(a: Int, b: Int): Double {
    val l1 = max(relLuminance(a), relLuminance(b))
    val l2 = min(relLuminance(a), relLuminance(b))
    return (l1 + 0.05) / (l2 + 0.05)
}

/** White or black — whichever reads better on [accent]. */
fun onAccentColor(accent: Int): Int =
    if (contrastRatio(accent, 0xFFFFFFFF.toInt()) >=
        contrastRatio(accent, 0xFF000000.toInt())
    ) {
        0xFFFFFFFF.toInt()
    } else {
        0xFF000000.toInt()
    }
