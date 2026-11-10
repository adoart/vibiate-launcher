package com.vibiate.launcher.data

import android.graphics.drawable.ColorDrawable
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacesTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2025, 11, day, hour, minute)

    // Nov 3 2025 = Monday … Nov 9 = Sunday.
    private val mon = at(3, 10)

    private fun signals(
        now: LocalDateTime = mon,
        headphones: Boolean = false,
        car: Boolean = false,
        location: LatLng? = null,
        home: SavedPlace? = null,
        work: SavedPlace? = null,
    ) = SpaceSignals(now, headphones, car, location, home, work)

    // ---- TimeWindow ----

    @Test
    fun `time window normal range`() {
        val t = TimeWindow(9 * 60, 18 * 60)
        assertTrue(t.matches(at(3, 10)))
        assertFalse(t.matches(at(3, 19)))
        // Saturday is excluded when the day mask is Mon–Fri only.
        val weekdays = TimeWindow(9 * 60, 18 * 60, days = 0x1F)
        assertFalse(weekdays.matches(at(8, 10)))
    }

    @Test
    fun `time window wraps midnight honoring yesterday day mask`() {
        // 22:00–06:00 Mon–Fri: 23:00 Monday = yes (Mon in mask);
        // 05:00 Tuesday = yes (yesterday Mon in mask);
        // 05:00 Sunday = no (yesterday Sat not in mask).
        val t = TimeWindow(22 * 60, 6 * 60, days = 0x1F)
        assertTrue(t.matches(at(3, 23)))
        assertTrue(t.matches(at(4, 5)))
        assertFalse(t.matches(at(9, 5)))
        assertFalse(t.matches(at(4, 12)))
    }

    @Test
    fun `time window all day still honors day mask`() {
        val all = TimeWindow(0, 0)
        assertTrue(all.matches(at(3, 3)))
        val weekdays = TimeWindow(0, 0, days = 0x1F)
        assertFalse(weekdays.matches(at(8, 3)))
    }

    // ---- SpaceDef.matches / matchReasons ----

    @Test
    fun `no configured triggers never matches`() {
        val s = SpaceDef(1, "X", SpaceGlyph.SUN)
        assertFalse(s.matches(signals()))
    }

    @Test
    fun `any vs all matching`() {
        val s = SpaceDef(
            1, "X", SpaceGlyph.SUN,
            time = TimeWindow(9 * 60, 18 * 60), headphones = true,
        )
        assertTrue(s.matches(signals(now = at(3, 10)))) // time yes, hp no
        assertFalse(
            s.copy(match = TriggerMatch.ALL)
                .matches(signals(now = at(3, 10))),
        )
        assertTrue(
            s.copy(match = TriggerMatch.ALL)
                .matches(signals(now = at(3, 10), headphones = true)),
        )
    }

    @Test
    fun `place triggers require the place and a fix`() {
        val work = SpaceDef(1, "X", SpaceGlyph.WORK, place = PlaceTrigger.WORK)
        val place = SavedPlace(40.0, -70.0, 200)
        val near = LatLng(40.0005, -70.0)
        val far = LatLng(41.0, -70.0)
        // Unset place -> false; no location -> false.
        assertFalse(work.matches(signals(location = near)))
        assertFalse(work.matches(signals(location = null, work = place)))
        assertTrue(
            work.matches(signals(location = near, work = place)),
        )
        assertFalse(work.matches(signals(location = far, work = place)))
        // Accuracy allowance extends the radius.
        assertTrue(
            work.matches(
                signals(
                    location = LatLng(40.002, -70.0, accuracyM = 400f),
                    work = place,
                ),
            ),
        )
    }

    @Test
    fun `away requires at least one set place`() {
        val away = SpaceDef(1, "X", SpaceGlyph.HOME, place = PlaceTrigger.AWAY)
        val place = SavedPlace(40.0, -70.0)
        val far = LatLng(41.0, -70.0)
        val near = LatLng(40.0005, -70.0)
        assertFalse(away.matches(signals(location = far))) // no places set
        assertTrue(away.matches(signals(location = far, work = place)))
        assertFalse(away.matches(signals(location = near, work = place)))
    }

    // ---- resolveSpaces ----

    private fun space(
        id: Int,
        auto: Boolean = true,
        match: Boolean = false,
        hp: Boolean = false,
    ) = SpaceDef(id, "S$id", SpaceGlyph.SUN, autoEnabled = auto, headphones = hp)

    @Test
    fun `default order puts Going Somewhere above Listening`() {
        val ids = defaultSpaces { "s$it" }.map { it.id }
        assertEquals(listOf(3, 4, 2, 1), ids)
        // Car stereo = A2DP: both triggers true, car wins by list order.
        val r = resolveSpaces(
            defaultSpaces { "s$it" },
            signals(headphones = true, car = true), true, 1, null,
        )
        assertEquals(3, r.spaceId)
    }

    @Test
    fun `priority is list order`() {
        val spaces = listOf(
            space(2, hp = true), space(1, hp = true), space(9),
        )
        val r = resolveSpaces(
            spaces, signals(headphones = true), true, 9, null,
        )
        assertEquals(2, r.spaceId)
        assertEquals(2, r.autoId)
        assertFalse(r.manual)
    }

    @Test
    fun `autoEnabled false is skipped`() {
        val spaces = listOf(space(1, auto = false, hp = true), space(9))
        val r = resolveSpaces(
            spaces, signals(headphones = true), true, 9, null,
        )
        assertEquals(9, r.spaceId) // fallback
        assertEquals(listOf(SpaceReason.FALLBACK), r.reasons)
    }

    @Test
    fun `missing fallback resolves to first space`() {
        val spaces = listOf(space(3), space(5))
        val r = resolveSpaces(spaces, signals(), true, 99, null)
        assertEquals(3, r.spaceId)
    }

    @Test
    fun `manual holds while auto winner unchanged and goes stale when it changes`() {
        val spaces = listOf(space(1, hp = true), space(9))
        val pick = ManualPick(9, autoIdAtPick = 1)
        val keep = resolveSpaces(
            spaces, signals(headphones = true), true, 9, pick,
        )
        assertEquals(9, keep.spaceId)
        assertTrue(keep.manual)
        assertFalse(keep.manualStale)
        // Auto winner changed (headphones off -> none): stale -> auto path.
        val stale = resolveSpaces(
            spaces, signals(headphones = false), true, 9, pick,
        )
        assertFalse(stale.manual)
        assertTrue(stale.manualStale)
        assertEquals(9, stale.spaceId) // falls to fallback
    }

    @Test
    fun `manual kept when auto switching is off`() {
        val spaces = listOf(space(1), space(9))
        val r = resolveSpaces(
            spaces, signals(), autoSwitch = false,
            fallbackId = 1, manual = ManualPick(9, autoIdAtPick = null),
        )
        assertEquals(9, r.spaceId)
        assertTrue(r.manual)
        assertNull(r.autoId)
    }

    // ---- Serialization ----

    @Test
    fun `space def serialize round trip`() {
        val def = SpaceDef(
            id = 7, name = "My|Space\nNow", glyph = SpaceGlyph.CAR,
            autoEnabled = false, match = TriggerMatch.ALL,
            time = TimeWindow(300, 660, 0x1F), headphones = true,
            place = PlaceTrigger.AWAY, car = true,
            showOnActivate = true,
            launchOnActivate = "pkg/Class\$Inner",
        )
        val back = SpaceDef.deserialize(def.serialize())!!
        assertEquals(def.copy(name = "My Space Now"), back)
        // Null time / launchKey round-trip.
        val minimal = SpaceDef(2, "m", SpaceGlyph.STAR)
        assertEquals(minimal, SpaceDef.deserialize(minimal.serialize())!!)
        assertNull(SpaceDef.deserialize("junk"))
        assertNull(SpaceDef.deserialize("s1|x|n|SUN|true|ANY||||false|NONE|false|false|"))
    }

    // ---- Haversine ----

    @Test
    fun `haversine one degree latitude is about 111km`() {
        val d = distanceMeters(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertTrue(d in 110_500.0..112_000.0)
    }

    // ---- defaultSpaceApps ----

    private fun app(key: String, cat: AppCategory) = AppEntry(
        "com.$key", "Main", key, ColorDrawable(), cat,
    )

    @Test
    fun `default space apps map to their categories`() {
        val apps = listOf(
            app("mail", AppCategory.PRODUCTIVITY),
            app("maps", AppCategory.TRANSIT),
            app("spotify", AppCategory.MUSIC),
            app("game", AppCategory.GAMES),
        )
        assertEquals(
            listOf("com.mail/Main"),
            defaultSpaceApps(2, apps),
        )
        assertEquals(
            listOf("com.maps/Main"),
            defaultSpaceApps(3, apps),
        )
        assertEquals(
            listOf("com.spotify/Main"),
            defaultSpaceApps(4, apps),
        )
    }
}
