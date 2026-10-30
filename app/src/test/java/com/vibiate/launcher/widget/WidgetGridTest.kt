package com.vibiate.launcher.widget

import com.vibiate.launcher.data.HOME_SCREEN_ID
import com.vibiate.launcher.data.WidgetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridTest {

    private fun span(c: Int, r: Int) = GridSpan(c, r)
    private fun pos(c: Int, r: Int) = GridPos(c, r)

    // ---- flowLayout ----

    @Test
    fun `full-width widgets stack in order`() {
        val out = flowLayout(listOf(span(4, 2), span(4, 1), span(4, 3)))
        assertEquals(listOf(pos(0, 0), pos(0, 2), pos(0, 3)), out)
    }

    @Test
    fun `two half-width widgets share a band`() {
        val out = flowLayout(listOf(span(2, 1), span(2, 1)))
        assertEquals(listOf(pos(0, 0), pos(2, 0)), out)
    }

    @Test
    fun `widget that does not fit wraps to a new band below the tallest`() {
        // 3-wide + 2-wide: the 2 wraps; band height is 2 (tall widget).
        val out = flowLayout(listOf(span(3, 2), span(2, 1), span(1, 1)))
        assertEquals(listOf(pos(0, 0), pos(0, 2), pos(2, 2)), out)
    }

    @Test
    fun `tall and short in same band - next band starts below the tallest`() {
        val out = flowLayout(
            listOf(span(1, 3), span(1, 1), span(4, 1)),
        )
        assertEquals(listOf(pos(0, 0), pos(1, 0), pos(0, 3)), out)
    }

    // ---- autoSpan ----

    @Test
    fun `autoSpan uses target cells when present`() {
        assertEquals(
            GridSpan(2, 3),
            autoSpan(
                minWidthDp = 0f, minHeightDp = 0f,
                targetCols = 2, targetRows = 3,
                cellWidthDp = 250f, cellHeightDp = 96f,
            ),
        )
    }

    @Test
    fun `autoSpan derives cells from min sizes`() {
        // cellW 250dp, cellH 96dp: 250dp min -> ceil((250+16)/250)=2 cols;
        // 80dp min -> ceil(96/96)=1 row
        assertEquals(
            GridSpan(2, 1),
            autoSpan(
                minWidthDp = 250f, minHeightDp = 80f,
                targetCols = 0, targetRows = 0,
                cellWidthDp = 250f, cellHeightDp = 96f,
            ),
        )
    }

    @Test
    fun `autoSpan clamps to grid`() {
        assertEquals(
            GridSpan(4, 8),
            autoSpan(
                minWidthDp = 0f, minHeightDp = 0f,
                targetCols = 9, targetRows = 20,
                cellWidthDp = 250f, cellHeightDp = 96f,
            ),
        )
    }

    // ---- clampSpan ----

    @Test
    fun `clampSpan keeps non-resizable axes at original`() {
        val original = span(2, 2)
        assertEquals(
            GridSpan(2, 4),
            clampSpan(
                span(3, 4), minCols = 1, minRows = 1,
                horizontalResizable = false, verticalResizable = true,
                original = original,
            ),
        )
    }

    @Test
    fun `clampSpan respects min and max`() {
        assertEquals(
            GridSpan(2, 2),
            clampSpan(span(1, 1), minCols = 2, minRows = 2, maxCols = 4, maxRows = 6),
        )
        assertEquals(
            GridSpan(4, 6),
            clampSpan(span(5, 9), minCols = 1, minRows = 1, maxCols = 4, maxRows = 6),
        )
    }

    // ---- WidgetEntry round-trip ----

    @Test
    fun `widget entry serializes and parses all fields`() {
        val e = WidgetEntry(7, "com.x", "com.x.W", screenId = 2, cols = 2, rows = 3, title = "Mine")
        assertEquals(e, WidgetEntry.deserialize(e.serialize()))
    }

    @Test
    fun `legacy 3 and 4 field lines still parse`() {
        assertEquals(
            WidgetEntry(5, "com.x", "com.x.W"),
            WidgetEntry.deserialize("5;com.x;com.x.W"),
        )
        assertEquals(
            WidgetEntry(5, "com.x", "com.x.W", screenId = 3),
            WidgetEntry.deserialize("5;com.x;com.x.W;3"),
        )
    }

    @Test
    fun `title containing semicolon survives`() {
        val e = WidgetEntry(9, "p", "p.C", title = "a;b;c")
        val parsed = WidgetEntry.deserialize(e.serialize())
        assertEquals("a;b;c", parsed?.title)
        assertEquals(9, parsed?.appWidgetId)
    }

    @Test
    fun `malformed numeric fields fall back to defaults`() {
        val e = WidgetEntry.deserialize("1;p;p.C;xx;zz;qq;T")
        assertEquals(WidgetEntry(1, "p", "p.C", 0, 0, 0, "T"), e)
        assertNull(WidgetEntry.deserialize("bad;p;p.C"))
    }

    @Test
    fun `newlines in title are sanitized`() {
        val e = WidgetEntry(1, "p", "p.C", title = "a\nb")
        assertTrue(!e.serialize().contains('\n'))
        assertEquals("a b", WidgetEntry.deserialize(e.serialize())?.title)
    }
}

class WidgetDragTest {

    private fun p(c: Int, r: Int) = GridPos(c, r)
    private fun s(c: Int, r: Int) = GridSpan(c, r)

    // ---- firstFreeSpot ----

    @Test
    fun `firstFreeSpot on empty grid is top-left`() {
        assertEquals(p(0, 0), firstFreeSpot(emptyList(), s(4, 1)))
    }

    @Test
    fun `firstFreeSpot fills a gap`() {
        val placed = listOf(p(0, 0) to s(4, 2), p(0, 3) to s(4, 1))
        assertEquals(p(0, 2), firstFreeSpot(placed, s(4, 1)))
    }

    @Test
    fun `firstFreeSpot skips narrow gaps for wide spans`() {
        // 2-wide gap at cols 1-2 between two 1-wide widgets on row 0.
        val placed = listOf(p(0, 0) to s(1, 1), p(3, 0) to s(1, 1))
        // A 2-wide span fills the gap; a 4-wide span goes to the next row.
        assertEquals(p(1, 0), firstFreeSpot(placed, s(2, 1)))
        assertEquals(p(0, 1), firstFreeSpot(placed, s(4, 1)))
    }

    // ---- placeWithPushDown ----

    @Test
    fun `drop into empty area moves nobody`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 2)),
            2 to (p(0, 2) to s(4, 1)),
        )
        val out = placeWithPushDown(items, 2, p(0, 4), s(4, 1))
        assertEquals(p(0, 0), out[1])
        assertEquals(p(0, 4), out[2])
    }

    @Test
    fun `drop onto a widget pushes it straight down`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 2)),
            2 to (p(0, 2) to s(4, 1)),
        )
        val out = placeWithPushDown(items, 2, p(0, 1), s(4, 1))
        assertEquals(p(0, 1), out[2])
        assertEquals(p(0, 2), out[1])
    }

    @Test
    fun `pushdown cascades`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 1)),
            2 to (p(0, 1) to s(4, 1)),
            3 to (p(0, 2) to s(4, 1)),
        )
        // Drop 3 on top of 1: 1 slides to row 1, which pushes 2 to row 2.
        val out = placeWithPushDown(items, 3, p(0, 0), s(4, 1))
        assertEquals(p(0, 0), out[3])
        assertEquals(p(0, 1), out[1])
        assertEquals(p(0, 2), out[2])
    }

    @Test
    fun `target column clamps to grid`() {
        val items = mapOf(1 to (p(0, 0) to s(2, 1)))
        val out = placeWithPushDown(items, 1, p(3, 0), s(2, 1))
        assertEquals(p(2, 0), out[1])
    }

    @Test
    fun `resize growth pushes neighbour down`() {
        val items = mapOf(
            1 to (p(0, 0) to s(4, 1)),
            2 to (p(0, 1) to s(4, 1)),
        )
        val out = placeWithPushDown(items, 1, p(0, 0), s(4, 2))
        assertEquals(p(0, 0), out[1])
        assertEquals(p(0, 2), out[2])
    }

    // ---- WidgetEntry v2 serialization ----

    @Test
    fun `v2 round trip keeps position`() {
        val e = WidgetEntry(
            7, "pkg", "Cls", 0, 4, 2, "title", col = 0, row = 3,
        )
        val back = WidgetEntry.deserialize(e.serialize())!!
        assertEquals(e, back)
        assertEquals(0, back.col)
        assertEquals(3, back.row)
    }

    @Test
    fun `legacy lines parse with unplaced position`() {
        val e = WidgetEntry.deserialize("7;pkg;Cls;0;4;2;hello")!!
        assertEquals(-1, e.col)
        assertEquals(-1, e.row)
        assertEquals("hello", e.title)
        // Legacy 3-field form too.
        assertEquals(-1, WidgetEntry.deserialize("7;pkg;Cls")!!.col)
    }

    @Test
    fun `v2 title containing semicolon survives`() {
        val e = WidgetEntry(
            7, "pkg", "Cls", 1, 2, 1, "a;b;c", col = 2, row = 1,
        )
        val back = WidgetEntry.deserialize(e.serialize())!!
        assertEquals("a;b;c", back.title)
        assertEquals(p(2, 1), GridPos(back.col, back.row))
    }
}

class HomeScreenIdTest {
    @Test
    fun `widget entry round-trips with home screen id`() {
        val e = WidgetEntry(9, "pkg", "Cls", HOME_SCREEN_ID, 2, 3, "t")
        assertEquals(e, WidgetEntry.deserialize(e.serialize()))
        assertEquals(HOME_SCREEN_ID, WidgetEntry.deserialize("9;pkg;Cls;-1")!!.screenId)
    }
}

class DefaultHomeWidgetsTest {
    private fun specs() = listOf(
        Triple(4, 2, listOf("p/A", "p/B")),
        Triple(4, 1, listOf("p/C")),
        Triple(4, 1, listOf("p/D", "p/E")),
    )

    @Test
    fun `all installed picks slot order`() {
        val r = pickDefaultHomeWidgets(
            installed = setOf("p/A", "p/B", "p/C", "p/D", "p/E"),
            presentOnHome = emptySet(),
            slots = specs(),
        )
        assertEquals(listOf("p/A", "p/C", "p/D"), r.map { it.first })
    }

    @Test
    fun `fallback candidate wins when first missing`() {
        val r = pickDefaultHomeWidgets(
            installed = setOf("p/B", "p/C", "p/E"),
            presentOnHome = emptySet(),
            slots = specs(),
        )
        assertEquals(listOf("p/B", "p/C", "p/E"), r.map { it.first })
    }

    @Test
    fun `slot skipped when no candidate installed`() {
        val r = pickDefaultHomeWidgets(
            installed = setOf("p/A", "p/D"),
            presentOnHome = emptySet(),
            slots = specs(),
        )
        assertEquals(listOf("p/A", "p/D"), r.map { it.first })
    }

    @Test
    fun `slot skipped when already present on home`() {
        val r = pickDefaultHomeWidgets(
            installed = setOf("p/A", "p/B", "p/C", "p/D"),
            presentOnHome = setOf("p/C"),
            slots = specs(),
        )
        assertEquals(listOf("p/A", "p/D"), r.map { it.first })
    }
}

