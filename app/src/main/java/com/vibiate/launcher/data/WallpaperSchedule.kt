package com.vibiate.launcher.data

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.random.Random

enum class WallpaperScheduleMode { INTERVAL, TIMES }

enum class WallpaperTarget { HOME, LOCK, BOTH }

/** Rotation interval presets, in minutes. All < 1440 presets divide 1440. */
val WALLPAPER_INTERVAL_PRESETS = listOf(
    15, 30, 60, 120, 180, 240, 360, 480, 720, 1440, 2880, 4320, 10080,
)

/**
 * When the wallpaper rotates. INTERVAL grids repeat every [intervalMinutes]
 * aligned to [startMinute] each day (< 1 day) or every interval/1440 days
 * counted from [anchorEpochDay] (>= 1 day). TIMES fires at the listed minutes
 * of day.
 */
data class WallpaperSchedule(
    val mode: WallpaperScheduleMode = WallpaperScheduleMode.INTERVAL,
    val intervalMinutes: Int = 1440,
    val startMinute: Int = 7 * 60,
    val anchorEpochDay: Long = 0,
    val times: List<Int> = listOf(7 * 60),
) {
    fun serialize(): String = buildString {
        append("ws1|").append(mode.name).append('|')
            .append(intervalMinutes).append('|')
            .append(startMinute).append('|')
            .append(anchorEpochDay).append('|')
            .append(times.joinToString(","))
    }

    companion object {
        fun deserialize(s: String): WallpaperSchedule? {
            val p = s.split('|')
            if (p.size < 6 || p[0] != "ws1") return null
            val mode = runCatching { WallpaperScheduleMode.valueOf(p[1]) }
                .getOrNull() ?: return null
            val interval = p[2].toIntOrNull() ?: return null
            val start = p[3].toIntOrNull() ?: return null
            val anchor = p[4].toLongOrNull() ?: return null
            val times = p[5].split(',')
                .mapNotNull { it.toIntOrNull() }
                .distinct().sorted()
            return WallpaperSchedule(mode, interval, start, anchor, times)
        }
    }
}

private fun atMinute(day: java.time.LocalDate, minute: Int): LocalDateTime =
    day.atTime(LocalTime.of(minute / 60, minute % 60))

/** First scheduled slot strictly after [now] (truncated to the minute). */
fun nextRotation(s: WallpaperSchedule, now: LocalDateTime): LocalDateTime? {
    val nowMinute = now.withSecond(0).withNano(0)
    val today = nowMinute.toLocalDate()
    return when (s.mode) {
        WallpaperScheduleMode.TIMES -> {
            if (s.times.isEmpty()) return null
            val dayMinutes = nowMinute.hour * 60 + nowMinute.minute
            val nextToday = s.times.firstOrNull { it > dayMinutes }
            if (nextToday != null) {
                atMinute(today, nextToday)
            } else {
                atMinute(today.plusDays(1), s.times.first())
            }
        }
        WallpaperScheduleMode.INTERVAL -> {
            if (s.intervalMinutes < 1440) {
                // Slots startMinute + k*interval (mod 1440) every day.
                val perDay = (1440 / s.intervalMinutes).coerceAtLeast(1)
                val slots = (0 until perDay)
                    .map { Math.floorMod(s.startMinute + it * s.intervalMinutes, 1440) }
                    .sorted()
                val dayMinutes = nowMinute.hour * 60 + nowMinute.minute
                val nextToday = slots.firstOrNull { it > dayMinutes }
                if (nextToday != null) {
                    atMinute(today, nextToday)
                } else {
                    atMinute(today.plusDays(1), slots.first())
                }
            } else {
                // Every interval/1440 days at startMinute from anchorEpochDay.
                val periodDays = (s.intervalMinutes / 1440).coerceAtLeast(1).toLong()
                val todayEpoch = today.toEpochDay()
                val k = Math.floorMod(todayEpoch - s.anchorEpochDay, periodDays)
                var day = todayEpoch - k // aligned day <= today
                var candidate = atMinute(
                    java.time.LocalDate.ofEpochDay(day), s.startMinute
                )
                if (!candidate.isAfter(nowMinute)) {
                    day += periodDays
                    candidate = atMinute(
                        java.time.LocalDate.ofEpochDay(day), s.startMinute
                    )
                }
                candidate
            }
        }
    }
}

/** Crop region in fractions of the EXIF-oriented image. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A rotation image; [crop] null = centre-crop fill. */
data class WallpaperImage(val uri: String, val crop: CropRect? = null)

/** Lines "wi1|l|t|r|b|uri" — uri LAST so `|` in it survives (split limit 6). */
fun serializeWallpaperImages(list: List<WallpaperImage>): String =
    list.joinToString("\n") { img ->
        val c = img.crop
        if (c == null) {
            "wi1|||||${img.uri}"
        } else {
            "wi1|${c.left}|${c.top}|${c.right}|${c.bottom}|${img.uri}"
        }
    }

fun deserializeWallpaperImages(s: String): List<WallpaperImage> =
    s.split('\n').mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val p = line.split('|', limit = 6)
        if (p.size < 6 || p[0] != "wi1") return@mapNotNull null
        val crop = if (
            p[1].isBlank() && p[2].isBlank() && p[3].isBlank() && p[4].isBlank()
        ) {
            null
        } else {
            val l = p[1].toFloatOrNull() ?: return@mapNotNull null
            val t = p[2].toFloatOrNull() ?: return@mapNotNull null
            val r = p[3].toFloatOrNull() ?: return@mapNotNull null
            val b = p[4].toFloatOrNull() ?: return@mapNotNull null
            CropRect(l, t, r, b)
        }
        val uri = p[5]
        if (uri.isBlank()) null else WallpaperImage(uri, crop)
    }

/** Append new uris, deduped by uri against the existing list. */
fun addWallpaperImages(
    existing: List<WallpaperImage>,
    uris: List<String>,
): List<WallpaperImage> =
    existing + uris.filter { u -> existing.none { it.uri == u } }
        .map { WallpaperImage(it) }

fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices || from == to) return list
    val l = list.toMutableList()
    l.add(to, l.removeAt(from))
    return l
}

/**
 * The image after [currentUri] in list order (wraps); missing/null current →
 * first. With [shuffle], a random image other than current (size 1 → that one).
 */
fun nextWallpaperImage(
    images: List<WallpaperImage>,
    currentUri: String?,
    shuffle: Boolean,
    random: Random,
): WallpaperImage? {
    if (images.isEmpty()) return null
    if (shuffle) {
        val candidates = if (images.size > 1) {
            images.filter { it.uri != currentUri }
        } else {
            images
        }
        return candidates[random.nextInt(candidates.size)]
    }
    val idx = images.indexOfFirst { it.uri == currentUri }
    return images[(idx + 1) % images.size]
}

// ---- Crop math (normalized image coords; aspects are width/height in px) ----

/** Largest centred rect of frame aspect inside the image. */
fun coverCrop(imageAspect: Float, frameAspect: Float): CropRect {
    val w: Float
    val h: Float
    if (imageAspect > frameAspect) {
        h = 1f
        w = frameAspect / imageAspect
    } else {
        w = 1f
        h = imageAspect / frameAspect
    }
    val l = (1f - w) / 2f
    val t = (1f - h) / 2f
    return CropRect(l, t, l + w, t + h)
}

/**
 * Largest rect of [frameAspect] (pixel aspect) centred inside [c]: shrinks
 * width or height only, then clamps inside 0..1. Guards against stored
 * crops saved for a different screen aspect (or by a stale bug) so the
 * render never stretches.
 */
fun fitCropToAspect(
    c: CropRect,
    imageAspect: Float,
    frameAspect: Float,
): CropRect {
    val w = c.right - c.left
    val h = c.bottom - c.top
    if (w <= 0f || h <= 0f || imageAspect <= 0f || frameAspect <= 0f) {
        return coverCrop(imageAspect, frameAspect)
    }
    // Required normalized ratio: (nw * imgW) / (nh * imgH) == frameAspect
    val required = frameAspect / imageAspect // == nw / nh
    val nw: Float
    val nh: Float
    if (w / h > required) {
        nh = h
        nw = h * required
    } else {
        nw = w
        nh = w / required
    }
    val cx = (c.left + c.right) / 2f
    val cy = (c.top + c.bottom) / 2f
    val l = (cx - nw / 2f).coerceIn(0f, 1f - nw)
    val t = (cy - nh / 2f).coerceIn(0f, 1f - nh)
    return CropRect(l, t, l + nw, t + nh)
}

/** Shift the crop by [dx]/[dy], clamped inside 0..1 (size unchanged). */
fun panCrop(c: CropRect, dx: Float, dy: Float): CropRect {
    val w = c.right - c.left
    val h = c.bottom - c.top
    val l = (c.left + dx).coerceIn(0f, 1f - w)
    val t = (c.top + dy).coerceIn(0f, 1f - h)
    return CropRect(l, t, l + w, t + h)
}

/**
 * [factor] > 1 zooms in: the crop shrinks about the focus point, size clamped
 * between [coverCrop] (max) and coverCrop/5 (min), frame aspect preserved,
 * then clamped inside 0..1.
 */
fun zoomCrop(
    c: CropRect,
    factor: Float,
    focusX: Float,
    focusY: Float,
    imageAspect: Float,
    frameAspect: Float,
): CropRect {
    val cover = coverCrop(imageAspect, frameAspect)
    val maxW = cover.right - cover.left
    val minW = maxW / 5f
    var w = (c.right - c.left) / factor
    if (w >= maxW) return cover
    w = w.coerceIn(minW, maxW)
    val h = w * imageAspect / frameAspect
    val fx = (focusX - c.left) / (c.right - c.left)
    val fy = (focusY - c.top) / (c.bottom - c.top)
    val l = (focusX - fx * w).coerceIn(0f, 1f - w)
    val t = (focusY - fy * h).coerceIn(0f, 1f - h)
    return CropRect(l, t, l + w, t + h)
}

/** Snap minutes to the closest supported preset (for settings fallback). */
fun nearestIntervalPreset(minutes: Long): Int =
    WALLPAPER_INTERVAL_PRESETS.minByOrNull { abs(it - minutes) } ?: 1440
