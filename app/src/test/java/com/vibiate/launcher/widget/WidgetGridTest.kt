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

    private fun e(id: Int, screenId: Int) = WidgetEntry(
        appWidgetId = id,
        providerPackage = "pkg$id",
        providerClass = "Cls$id",
        screenId = screenId,
    )

    @Test
    fun `hitIndex returns rect containing point`() {
        val rects = listOf(
            GridRect(0, 0, 100, 50),
            GridRect(0, 54, 100, 104),
        )
        assertEquals(0, hitIndex(rects, 50, 25))
        assertEquals(1, hitIndex(rects, 50, 80))
        assertNull(hitIndex(rects, 50, 120))
        assertNull(hitIndex(rects, 150, 25))
        // Edge of a rect is outside its inner 70% hysteresis band.
        assertNull(hitIndex(rects, 2, 25))
        assertNull(hitIndex(rects, 98, 25))
    }

    @Test
    fun `moved reorders forward backward and same index`() {
        val l = listOf(1, 2, 3, 4)
        assertEquals(listOf(2, 3, 1, 4), l.moved(0, 2))
        assertEquals(listOf(4, 1, 2, 3), l.moved(3, 0))
        assertEquals(l, l.moved(1, 1))
        assertEquals(l, l.moved(9, 0))
    }

    @Test
    fun `mergeScreenOrder reorders only that screen`() {
        val all = listOf(
            e(1, 0), e(2, 1), e(3, 0), e(4, 0), e(5, 1),
        )
        val merged = mergeScreenOrder(all, 0, listOf(4, 3, 1))
        assertEquals(listOf(4, 2, 3, 1, 5), merged.map { it.appWidgetId })
        // Missing ids leave the slot as-is; extra ids are ignored.
        val partial = mergeScreenOrder(all, 0, listOf(4, 3))
        assertEquals(listOf(4, 2, 3, 1, 5), partial.map { it.appWidgetId })
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

class DragReorderTest {
    // Three 1x1 rects stacked vertically, 100px each with 10px gaps.
    private val rects = listOf(
        GridRect(0, 0, 100, 100),
        GridRect(0, 110, 100, 210),
        GridRect(0, 220, 100, 320),
    )
    private val order = listOf(1, 2, 3)
    private val bottom = 320

    @Test
    fun `hitting another widget centre moves dragged to that index`() {
        // Drag id 3 over rect[0]'s centre.
        assertEquals(
            listOf(3, 1, 2),
            dragReorder(order, rects, 3, 50, 50, bottom),
        )
    }

    @Test
    fun `finger inside dragged widget own slot is unchanged`() {
        assertEquals(order, dragReorder(order, rects, 2, 50, 160, bottom))
    }

    @Test
    fun `edge margin is ignored`() {
        // 2% inside rect[0]'s top edge — within the 15% hysteresis margin.
        assertEquals(order, dragReorder(order, rects, 3, 50, 2, bottom))
    }

    @Test
    fun `below content bottom moves to end`() {
        assertEquals(
            listOf(2, 3, 1),
            dragReorder(order, rects, 1, 50, 400, bottom),
        )
    }

    @Test
    fun `already last below bottom is unchanged`() {
        assertEquals(order, dragReorder(order, rects, 3, 50, 400, bottom))
    }

    @Test
    fun `mixed layout tall over short neighbour`() {
        // 4x2 at top (tall), then two 2x1 side by side.
        val spans = listOf(GridSpan(4, 2), GridSpan(2, 1), GridSpan(2, 1))
        val cellW = 100; val cellH = 100; val gap = 10
        val positions = flowLayout(spans)
        val rects = spans.mapIndexed { i, s ->
            val p = positions[i]
            val l = p.col * (cellW + gap); val t = p.row * (cellH + gap)
            GridRect(l, t, l + s.cols * cellW + (s.cols - 1) * gap,
                     t + s.rows * cellH + (s.rows - 1) * gap)
        }
        // Drag the tall 4x2 (id 1) over the second 2x1 (id 3) -> index 2.
        assertEquals(
            listOf(2, 3, 1),
            dragReorder(listOf(1, 2, 3), rects, 1,
                        3 * cellW, 2 * cellH + gap + cellH / 2, rects.last().bottom),
        )
    }
}
