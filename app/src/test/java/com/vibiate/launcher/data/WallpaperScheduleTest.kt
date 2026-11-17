package com.vibiate.launcher.data

import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperScheduleTest {

    private fun t(day: Int, minute: Int) =
        LocalDateTime.of(2025, 1, 1, 0, 0).plusDays(day.toLong())
            .plusMinutes(minute.toLong())

    // ---- nextRotation: INTERVAL < 1 day ----

    @Test
    fun interval6h_slots() {
        val s = WallpaperSchedule(intervalMinutes = 360, startMinute = 7 * 60)
        assertEquals(t(0, 7 * 60), nextRotation(s, t(0, 6 * 60 + 59)))
        assertEquals(t(0, 13 * 60), nextRotation(s, t(0, 7 * 60)))
        // past last slot -> first slot tomorrow (07:00 + 18h = 01:00)
        assertEquals(t(1, 60), nextRotation(s, t(0, 23 * 60 + 30)))
    }

    @Test
    fun interval15min() {
        val s = WallpaperSchedule(intervalMinutes = 15, startMinute = 0)
        assertEquals(t(0, 10 * 60 + 15), nextRotation(s, t(0, 10 * 60 + 7)))
    }

    // ---- nextRotation: INTERVAL >= 1 day ----

    @Test
    fun intervalDaily() {
        val s = WallpaperSchedule(intervalMinutes = 1440, startMinute = 7 * 60)
        assertEquals(t(1, 7 * 60), nextRotation(s, t(0, 8 * 60)))
        assertEquals(t(0, 7 * 60), nextRotation(s, t(0, 6 * 60)))
    }

    @Test
    fun intervalEvery2Days() {
        val anchor = t(0, 0).toLocalDate().toEpochDay()
        val s = WallpaperSchedule(
            intervalMinutes = 2880, startMinute = 8 * 60,
            anchorEpochDay = anchor,
        )
        // On the aligned day after the slot -> next aligned day.
        assertEquals(t(2, 8 * 60), nextRotation(s, t(0, 9 * 60)))
        // Day before the anchor (negative k): aligned days are D, D+2, ...
        // so the next slot strictly after D-1 10:00 is D 08:00.
        assertEquals(t(0, 8 * 60), nextRotation(s, t(-1, 10 * 60)))
        // Day between two aligned days.
        assertEquals(t(2, 8 * 60), nextRotation(s, t(1, 10 * 60)))
    }

    // ---- nextRotation: TIMES ----

    @Test
    fun timesMode() {
        val s = WallpaperSchedule(
            mode = WallpaperScheduleMode.TIMES,
            times = listOf(7 * 60, 12 * 60 + 30, 19 * 60),
        )
        assertEquals(t(0, 19 * 60), nextRotation(s, t(0, 12 * 60 + 30)))
        assertEquals(t(1, 7 * 60), nextRotation(s, t(0, 20 * 60)))
    }

    @Test
    fun timesEmpty() {
        val s = WallpaperSchedule(
            mode = WallpaperScheduleMode.TIMES, times = emptyList(),
        )
        assertNull(nextRotation(s, t(0, 10 * 60)))
    }

    // ---- schedule serialization ----

    @Test
    fun scheduleRoundTrip() {
        val s = WallpaperSchedule(
            mode = WallpaperScheduleMode.TIMES,
            intervalMinutes = 360,
            startMinute = 420,
            anchorEpochDay = 20000,
            times = listOf(60, 420, 1140),
        )
        assertEquals(s, WallpaperSchedule.deserialize(s.serialize()))
    }

    @Test
    fun scheduleGarbage() {
        assertNull(WallpaperSchedule.deserialize(""))
        assertNull(WallpaperSchedule.deserialize("ws2|INTERVAL|60|0|0|60"))
        assertNull(WallpaperSchedule.deserialize("ws1|BOGUS|60|0|0|60"))
        assertNull(WallpaperSchedule.deserialize("ws1|INTERVAL|x|0|0|60"))
    }

    // ---- image serialization ----

    @Test
    fun imagesRoundTrip() {
        val list = listOf(
            WallpaperImage("content://a/1", CropRect(0.1f, 0.2f, 0.9f, 0.8f)),
            WallpaperImage("content://a/2|weird"),
            WallpaperImage("file:///p/3.png", null),
        )
        val parsed = deserializeWallpaperImages(serializeWallpaperImages(list))
        assertEquals(list, parsed)
    }

    @Test
    fun imagesSkipsMalformed() {
        val s = "wi1|0.1|0.2|0.9|0.8|content://ok\n" +
            "garbage line\n" +
            "wi1|x|0.2|0.9|0.8|content://bad\n" +
            "wi1|||||content://nocrop"
        val parsed = deserializeWallpaperImages(s)
        assertEquals(2, parsed.size)
        assertEquals("content://ok", parsed[0].uri)
        assertEquals(CropRect(0.1f, 0.2f, 0.9f, 0.8f), parsed[0].crop)
        assertEquals("content://nocrop", parsed[1].uri)
        assertNull(parsed[1].crop)
    }

    // ---- addWallpaperImages / moveItem ----

    @Test
    fun addDedupeAppend() {
        val existing = listOf(WallpaperImage("a"), WallpaperImage("b"))
        val out = addWallpaperImages(existing, listOf("b", "c", "d"))
        assertEquals(listOf("a", "b", "c", "d"), out.map { it.uri })
    }

    @Test
    fun moveBothDirections() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), moveItem(l, 1, 3))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(l, 3, 0))
        assertEquals(l, moveItem(l, 1, 1))
        assertEquals(l, moveItem(l, -1, 2))
    }

    // ---- nextWallpaperImage ----

    @Test
    fun nextInOrderWraps() {
        val imgs = listOf("a", "b", "c").map { WallpaperImage(it) }
        assertEquals("b", nextWallpaperImage(imgs, "a", false, Random(1))?.uri)
        assertEquals("a", nextWallpaperImage(imgs, "c", false, Random(1))?.uri)
    }

    @Test
    fun nextMissingCurrentFirst() {
        val imgs = listOf("a", "b").map { WallpaperImage(it) }
        assertEquals("a", nextWallpaperImage(imgs, null, false, Random(1))?.uri)
        assertEquals("a", nextWallpaperImage(imgs, "zzz", false, Random(1))?.uri)
    }

    @Test
    fun shuffleNeverRepeats() {
        val imgs = listOf("a", "b", "c").map { WallpaperImage(it) }
        val r = Random(42)
        repeat(200) {
            val n = nextWallpaperImage(imgs, "a", true, r)
            assertTrue(n != null && n.uri != "a")
        }
        val one = listOf(WallpaperImage("a"))
        assertEquals("a", nextWallpaperImage(one, "a", true, r)?.uri)
        assertNull(nextWallpaperImage(emptyList(), "a", true, r))
    }

    // ---- crop math ----

    @Test
    fun coverWideAndTall() {
        // Wide image (2:1) in a portrait frame (0.5): central 0.25-wide band.
        val c = coverCrop(2f, 0.5f)
        assertEquals(0.375f, c.left, 0.001f)
        assertEquals(0f, c.top, 0.001f)
        assertEquals(0.625f, c.right, 0.001f)
        assertEquals(1f, c.bottom, 0.001f)
        // Tall image (0.5) in a wide frame (2): central 0.25-tall band.
        val c2 = coverCrop(0.5f, 2f)
        assertEquals(0f, c2.left, 0.001f)
        assertEquals(0.375f, c2.top, 0.001f)
        assertEquals(1f, c2.right, 0.001f)
        assertEquals(0.625f, c2.bottom, 0.001f)
        // Crop aspect matches frame.
        val aspect = ((c.right - c.left) * 2f) / ((c.bottom - c.top) * 1f)
        assertEquals(0.5f, aspect, 0.001f)
    }

    @Test
    fun panClamps() {
        val c = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val p = panCrop(c, 1f, -1f)
        assertEquals(0.5f, p.left, 0.001f)
        assertEquals(0f, p.top, 0.001f)
        assertEquals(1f, p.right, 0.001f)
        assertEquals(0.5f, p.bottom, 0.001f)
    }

    @Test
    fun zoomInHalvesAroundFocus() {
        val cover = coverCrop(1f, 0.5f) // square image, portrait frame
        val z = zoomCrop(cover, 2f, 0.5f, 0.5f, 1f, 0.5f)
        assertEquals((cover.right - cover.left) / 2, z.right - z.left, 0.001f)
        assertEquals(0.375f, z.left, 0.001f)
        // Aspect preserved: (w*imgW)/(h*imgH) == frameAspect (square img).
        assertEquals(0.5f, (z.right - z.left) / (z.bottom - z.top), 0.001f)
    }

    @Test
    fun zoomOutClampsToCover() {
        val cover = coverCrop(1f, 0.5f)
        val z = zoomCrop(cover, 0.25f, 0.5f, 0.5f, 1f, 0.5f)
        assertEquals(cover, z)
    }

    @Test
    fun zoomInClampsAt5x() {
        val cover = coverCrop(1f, 0.5f)
        val z = zoomCrop(cover, 100f, 0.5f, 0.5f, 1f, 0.5f)
        assertEquals(
            (cover.right - cover.left) / 5, z.right - z.left, 0.001f,
        )
    }

    @Test
    fun fitAlreadyCorrectUnchanged() {
        val c = coverCrop(2f, 0.5f)
        val f = fitCropToAspect(c, 2f, 0.5f)
        assertEquals(c.left, f.left, 0.001f)
        assertEquals(c.top, f.top, 0.001f)
        assertEquals(c.right, f.right, 0.001f)
        assertEquals(c.bottom, f.bottom, 0.001f)
    }

    @Test
    fun fitTooWideNarrowsAroundCentre() {
        // Square image, portrait frame 0.5 → required w/h = 0.5.
        // Crop 0.6 wide, 0.5 tall (w/h 1.2, too wide): keep height,
        // width becomes 0.5 * 0.5 = 0.25, centred on cx = 0.5.
        val f = fitCropToAspect(
            CropRect(0.2f, 0.25f, 0.8f, 0.75f), 1f, 0.5f,
        )
        assertEquals(0.25f, f.right - f.left, 0.001f)
        assertEquals(0.5f, f.bottom - f.top, 0.001f)
        assertEquals(0.5f, (f.left + f.right) / 2f, 0.001f)
        // Pixel aspect now matches the frame.
        assertEquals(
            0.5f,
            (f.right - f.left) * 1f / ((f.bottom - f.top) * 1f),
            0.001f,
        )
    }

    @Test
    fun fitTooTallShortensAroundCentre() {
        // Wide image 2:1, wide frame 2 → required w/h = 1.
        // Crop 0.5 wide, 1.0 tall (w/h 0.5, too tall): keep width,
        // height becomes 0.5, centred on cy = 0.5.
        val f = fitCropToAspect(
            CropRect(0.25f, 0f, 0.75f, 1f), 2f, 2f,
        )
        assertEquals(0.5f, f.right - f.left, 0.001f)
        assertEquals(0.5f, f.bottom - f.top, 0.001f)
        assertEquals(0.5f, (f.top + f.bottom) / 2f, 0.001f)
        // Pixel aspect: (0.5 * 2) / (0.5 * 1) = 2 == frameAspect.
        assertEquals(
            2f,
            (f.right - f.left) * 2f / ((f.bottom - f.top) * 1f),
            0.001f,
        )
    }

    @Test
    fun fitClampsInsideBounds() {
        // Off-centre crop needing shrink near the edge stays in 0..1.
        val f = fitCropToAspect(
            CropRect(0.0f, 0.0f, 0.6f, 0.6f), 1f, 0.5f,
        )
        val w = f.right - f.left
        val h = f.bottom - f.top
        assertTrue(f.left >= -0.001f)
        assertTrue(f.top >= -0.001f)
        assertTrue(f.right <= 1.001f)
        assertTrue(f.bottom <= 1.001f)
        assertEquals(0.5f, w * 1f / (h * 1f), 0.001f)
    }
}
