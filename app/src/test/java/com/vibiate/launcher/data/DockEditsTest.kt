package com.vibiate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockEditsTest {

    private fun slots(vararg keys: String?): List<String?> =
        List(DOCK_MAX) { keys.getOrNull(it) }

    @Test
    fun place_into_empty_spot_moves_nothing_else() {
        val s = slots("a", "b", "c")
        assertEquals(
            slots("a", "b", "c", null, "x"),
            dockPlace(s, "x", 4),
        )
        // Holes before the target keep the icon exactly at the target.
        assertEquals(
            slots("a", "b", "x", "c"),
            dockPlace(s, "x", 2),
        )
    }

    @Test
    fun place_onto_occupied_shifts_toward_nearest_hole() {
        // Hole to the right: a,b,c,_,_,... drop on b (1) -> a,x,b,c,_,...
        val s = slots("a", "b", "c")
        assertEquals(
            slots("a", "x", "b", "c"),
            dockPlace(s, "x", 1),
        )
        // Only hole to the left: drop on c (3) shifts a,b,c left toward it.
        val s2 = slots(null, "a", "b", "c", "d", "e", "f", "g", "h", "i")
        assertEquals(
            slots("a", "b", "c", "x", "d", "e", "f", "g", "h", "i"),
            dockPlace(s2, "x", 3),
        )
    }

    @Test
    fun nearest_hole_tie_prefers_higher_index() {
        // holes at 0 and 2, drop on occupied 1: tie -> higher index (2).
        val s = slots(null, "a", null, "b")
        val out = dockPlace(s.replaceAt(1, "a"), "x", 1)
        assertEquals("x", out[1])
        assertEquals("a", out[2])
        assertNull(out[0])
    }

    @Test
    fun dock_move_onto_occupied_shifts_toward_vacated_source() {
        val s = slots("a", "b", "c", "d")
        // Move a (0) onto c's spot (2): holes at 0 and 4 tie on distance,
        // the higher one wins — b,c shift right into it.
        assertEquals(
            slots(null, "b", "a", "c", "d"),
            dockPlace(s, "a", 2, fromIndex = 0),
        )
        // Move d (3) onto a's spot (0): a,b,c shift right toward hole 3.
        assertEquals(
            slots("d", "a", "b", "c"),
            dockPlace(s, "d", 0, fromIndex = 3),
        )
    }

    @Test
    fun place_existing_key_moves_without_duplicates() {
        val s = slots("a", "b", "c")
        // Outside drop of a key already in the dock -> move, no duplicate.
        val out = dockPlace(s, "a", 2)
        assertEquals("a", out[2])
        assertEquals(1, out.count { it == "a" })
    }

    @Test
    fun place_new_into_full_replaces_at_index() {
        val s = slots("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        val out = dockPlace(s, "x", 4)
        assertEquals("x", out[4])
        assertEquals(10, out.count { it != null })
        assertEquals(0, out.count { it == "e" })
    }

    @Test
    fun place_same_slot_unchanged() {
        val s = slots("a", "b")
        assertEquals(s, dockPlace(s, "a", 0, fromIndex = 0))
        assertEquals(s, dockPlace(s, "x", 0, fromIndex = 0))
    }

    @Test
    fun remove_leaves_hole() {
        val s = slots("a", "b", "c")
        assertEquals(slots("a", null, "c"), dockRemove(s, "b"))
        assertEquals(s, dockRemove(s, "z"))
    }

    @Test
    fun codec_keeps_holes_and_normalizes() {
        val s = slots("pkg/a.Main", null, "b/c.D")
        assertEquals(s, decodeDockApps(encodeDockApps(s)))
        assertEquals(DOCK_MAX, decodeDockApps(encodeDockApps(s))!!.size)
        // Legacy compact list maps to slots 0..n-1.
        assertEquals(
            slots("pkg/a.Main", "b/c.D"),
            decodeDockApps("pkg/a.Main\nb/c.D"),
        )
        assertNull(decodeDockApps(null))
        // Short form normalizes; later duplicates become holes.
        assertEquals(
            slots("pkg/pkg.Main", null, "b/c.D"),
            decodeDockApps("pkg/.Main\npkg/pkg.Main\nb/c.D"),
        )
    }

    @Test
    fun normalize_dock_keys() {
        assertEquals("pkg/pkg.Main", normalizeDockKey("pkg/.Main"))
        assertEquals("pkg/pkg.Main", normalizeDockKey("pkg/pkg.Main"))
        assertEquals("a/b.C", normalizeDockKey("a/b.C"))
        assertNull(normalizeDockKey("noSlash"))
        assertNull(normalizeDockKey("pkg/"))
        assertNull(normalizeDockKey("/Main"))
    }

    private fun List<String?>.replaceAt(i: Int, v: String?): List<String?> =
        mapIndexed { j, k -> if (j == i) v else k }
}
