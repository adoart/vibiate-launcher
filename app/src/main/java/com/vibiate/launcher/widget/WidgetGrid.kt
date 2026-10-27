package com.vibiate.launcher.widget

import com.vibiate.launcher.data.WidgetEntry
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Widgets pages lay out on a fixed 4-column cell grid. */
const val WIDGET_COLUMNS = 4
const val WIDGET_MAX_ROWS = 8

/** Cell span of a widget on the grid. */
data class GridSpan(val cols: Int, val rows: Int)

/** Cell position of a widget on the grid (0-based). */
data class GridPos(val col: Int, val row: Int)

/**
 * Greedy left-to-right, top-to-bottom flow of spans onto a [columns]-wide
 * grid, preserving order. A widget that doesn't fit the remaining width of
 * the current row-band starts a new band placed below the tallest widget in
 * the band; widgets within a band are top-aligned.
 */
fun flowLayout(spans: List<GridSpan>, columns: Int = WIDGET_COLUMNS): List<GridPos> {
    val pos = ArrayList<GridPos>(spans.size)
    var bandTop = 0
    var bandHeight = 0
    var cursor = 0
    for (span in spans) {
        val cols = span.cols.coerceIn(1, columns)
        val rows = span.rows.coerceAtLeast(1)
        if (cursor + cols > columns) {
            bandTop += bandHeight
            bandHeight = 0
            cursor = 0
        }
        pos += GridPos(cursor, bandTop)
        cursor += cols
        bandHeight = max(bandHeight, rows)
    }
    return pos
}

/**
 * Default span for a provider: `targetCell{Width,Height}` (API 31+) win when
 * > 0, otherwise `ceil((minDp + 16) / cellDp)` against the actual cell size.
 * Result is clamped to 1..[maxCols] × 1..[WIDGET_MAX_ROWS].
 */
fun autoSpan(
    minWidthDp: Float,
    minHeightDp: Float,
    targetCols: Int,
    targetRows: Int,
    cellWidthDp: Float,
    cellHeightDp: Float,
    maxCols: Int = WIDGET_COLUMNS,
): GridSpan {
    val cols = if (targetCols > 0) targetCols
        else ceil((minWidthDp + 16f) / cellWidthDp).toInt()
    val rows = if (targetRows > 0) targetRows
        else ceil((minHeightDp + 16f) / cellHeightDp).toInt()
    return GridSpan(
        cols.coerceIn(1, maxCols),
        rows.coerceIn(1, WIDGET_MAX_ROWS),
    )
}

/**
 * Clamp a resize drag's candidate span to the provider's limits: an axis the
 * provider marks non-resizable keeps [original]'s value; otherwise the value
 * is coerced to [minCells..maxCells].
 */
fun clampSpan(
    span: GridSpan,
    minCols: Int,
    minRows: Int,
    maxCols: Int = WIDGET_COLUMNS,
    maxRows: Int = WIDGET_MAX_ROWS,
    horizontalResizable: Boolean = true,
    verticalResizable: Boolean = true,
    original: GridSpan = span,
): GridSpan = GridSpan(
    if (horizontalResizable) span.cols.coerceIn(minCols, maxCols)
    else original.cols,
    if (verticalResizable) span.rows.coerceIn(minRows, maxRows)
    else original.rows,
)

/** Convert a pixel size to whole cells of [cellDp] (plus gap allowance). */
fun pxToCells(px: Int, density: Float, cellDp: Float): Int =
    if (px <= 0) 0 else ceil((px / density + 16f) / cellDp).toInt()

/** Axis-aligned rect on the widget grid, in pixels. */
data class GridRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Index of the rect whose inner 70% contains ([x], [y]), else null. The 15%
 * edge margin is hysteresis so reorders don't oscillate between slots.
 */
fun hitIndex(rects: List<GridRect>, x: Int, y: Int): Int? =
    rects.indexOfFirst { r ->
        val mx = (r.right - r.left) * 0.15f
        val my = (r.bottom - r.top) * 0.15f
        x >= r.left + mx && x <= r.right - mx &&
            y >= r.top + my && y <= r.bottom - my
    }.takeIf { it >= 0 }

/** Move the element at [from] to [to]; unchanged list for invalid indices. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || from == to) return this
    val l = toMutableList()
    val t = l.removeAt(from)
    l.add(to.coerceIn(0, l.size), t)
    return l
}

/**
 * Reinsert one screen's widgets in [orderedIds] order into the global list,
 * keeping other screens' entries at their relative positions.
 */
fun mergeScreenOrder(
    all: List<WidgetEntry>,
    screenId: Int,
    orderedIds: List<Int>,
): List<WidgetEntry> {
    val byId = all.associateBy { it.appWidgetId }
    val queued = orderedIds.mapNotNull { byId[it] }
        .filter { it.screenId == screenId }
    val queuedIds = queued.mapTo(HashSet()) { it.appWidgetId }
    // Named entries first; any screen entries not listed keep their original
    // relative order in the remaining slots.
    val queue = ArrayDeque(
        queued + all.filter { it.screenId == screenId && it.appWidgetId !in queuedIds }
    )
    return all.map { e ->
        if (e.screenId == screenId) queue.removeFirstOrNull() ?: e else e
    }
}
