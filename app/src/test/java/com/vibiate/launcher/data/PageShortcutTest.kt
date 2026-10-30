package com.vibiate.launcher.data

import com.vibiate.launcher.widget.GridPos
import com.vibiate.launcher.widget.GridSpan
import com.vibiate.launcher.widget.dropPlacement
import com.vibiate.launcher.widget.shortcutDropId
import com.vibiate.launcher.widget.shortcutGridId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageShortcutTest {

    @Test
    fun `codec round trip`() {
        val s = PageShortcut(3, "pkg/pkg.Main", -1, 2, 5)
        assertEquals(s, PageShortcut.deserialize(s.serialize()))
    }

    @Test
    fun `componentKey with dollar and dots survives`() {
        val s = PageShortcut(7, "a.b/a.b.Outer\$Inner.Activity", 0, 1, 2)
        assertEquals(
            "a.b/a.b.Outer\$Inner.Activity",
            PageShortcut.deserialize(s.serialize())!!.componentKey,
        )
    }

    @Test
    fun `malformed lines return null`() {
        assertNull(PageShortcut.deserialize("1;0"))
        assertNull(PageShortcut.deserialize("x;0;1;2;pkg/pkg.Main"))
        assertNull(PageShortcut.deserialize("1;0;1;2;noSlash"))
        assertNull(PageShortcut.deserialize(""))
    }

    // ---- drop placement with mixed ids ----

    private fun p(c: Int, r: Int) = GridPos(c, r)
    private fun s(c: Int, r: Int) = GridSpan(c, r)

    @Test
    fun `new shortcut into empty cell moves nothing else`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 2)),
            2 to (p(0, 2) to s(4, 1)),
        )
        val out = dropPlacement(items, shortcutGridId(9), p(1, 3))
        assertEquals(p(0, 0), out[1])
        assertEquals(p(0, 2), out[2])
        assertEquals(p(1, 3), out[shortcutGridId(9)])
    }

    @Test
    fun `drop onto a widget pushes it down`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 2)),
            shortcutGridId(2) to (p(0, 2) to s(1, 1)),
        )
        val out = dropPlacement(items, shortcutGridId(2), p(0, 0))
        assertEquals(p(0, 0), out[shortcutGridId(2)])
        // The 4x2 widget slides only far enough to clear the 1x1 icon.
        assertEquals(p(0, 1), out[1])
    }

    @Test
    fun `drop onto another shortcut pushes it down`() {
        val items = mapOf(
            shortcutGridId(1) to (p(0, 0) to s(1, 1)),
        )
        val out = dropPlacement(items, shortcutGridId(5), p(0, 0))
        assertEquals(p(0, 0), out[shortcutGridId(5)])
        assertEquals(p(0, 1), out[shortcutGridId(1)])
    }

    @Test
    fun `moving existing shortcut vacates its old cell`() {
        val items = mapOf(
            shortcutGridId(1) to (p(0, 0) to s(1, 1)),
            shortcutGridId(2) to (p(1, 0) to s(1, 1)),
        )
        val out = dropPlacement(items, shortcutGridId(1), p(2, 2))
        assertEquals(p(2, 2), out[shortcutGridId(1)])
        assertEquals(p(1, 0), out[shortcutGridId(2)])
    }

    // ---- dedup rule ----

    @Test
    fun `same component on page moves instead of duplicating`() {
        assertEquals(4, shortcutDropId(sameComponentId = 4, null, 9))
        assertEquals(3, shortcutDropId(null, 3, 9))
        assertEquals(9, shortcutDropId(null, null, 9))
    }
}
