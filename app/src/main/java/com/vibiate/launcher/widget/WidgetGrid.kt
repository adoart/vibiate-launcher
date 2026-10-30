package com.vibiate.launcher.widget

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

/** Page shortcuts share the widget grid as negative ids (always span 1×1). */
fun shortcutGridId(id: Int): Int = -id
fun shortcutIdOf(gridId: Int): Int? = if (gridId < 0) -gridId else null

/**
 * The shortcut id a drop acts on: a shortcut for the same app already on the
 * page moves it (no duplicates), then the dragged shortcut, else a fresh id.
 */
fun shortcutDropId(
    sameComponentId: Int?,
    movingShortcutId: Int?,
    nextId: Int,
): Int = sameComponentId ?: movingShortcutId ?: nextId

/**
 * Positions after a 1×1 shortcut lands at [target] on [items] (widgets by
 * appWidgetId, shortcuts by -id): the drop point is clamped onto the grid
 * and colliders are pushed down. Used for both the live preview and the
 * committed drop so they always agree.
 */
fun dropPlacement(
    items: Map<Int, Pair<GridPos, GridSpan>>,
    movingGridId: Int,
    target: GridPos,
): Map<Int, GridPos> =
    placeWithPushDown(items, movingGridId, target, GridSpan(1, 1))

/** True when the span at [pos] overlaps any of [placed]. */
private fun overlaps(
    placed: Collection<Pair<GridPos, GridSpan>>,
    pos: GridPos,
    span: GridSpan,
): Boolean = placed.any { (p, s) ->
    pos.col < p.col + s.cols && p.col < pos.col + span.cols &&
        pos.row < p.row + s.rows && p.row < pos.row + span.rows
}

/**
 * First free spot in row-major order where [span] fits on [columns] without
 * overlapping [placed].
 */
fun firstFreeSpot(
    placed: List<Pair<GridPos, GridSpan>>,
    span: GridSpan,
    columns: Int = WIDGET_COLUMNS,
): GridPos {
    val s = GridSpan(span.cols.coerceIn(1, columns), span.rows.coerceAtLeast(1))
    var row = 0
    while (true) {
        for (col in 0..columns - s.cols) {
            val pos = GridPos(col, row)
            if (!overlaps(placed, pos, s)) return pos
        }
        row++
    }
}

/**
 * Positions after [movedId] is dropped at [target]: it is clamped onto the
 * grid (col to 0..columns − span.cols, row ≥ 0), then the other items in
 * (row, col) order keep their spot unless they overlap something already
 * placed — they slide straight down to the first free row. Deterministic;
 * items that don't collide never move.
 */
fun placeWithPushDown(
    items: Map<Int, Pair<GridPos, GridSpan>>,
    movedId: Int,
    target: GridPos,
    movedSpan: GridSpan,
    columns: Int = WIDGET_COLUMNS,
): Map<Int, GridPos> {
    val span = GridSpan(
        movedSpan.cols.coerceIn(1, columns),
        movedSpan.rows.coerceAtLeast(1),
    )
    val placed = LinkedHashMap<Int, Pair<GridPos, GridSpan>>()
    placed[movedId] = GridPos(
        target.col.coerceIn(0, columns - span.cols),
        target.row.coerceAtLeast(0),
    ) to span
    items.entries
        .filter { it.key != movedId }
        .sortedWith(compareBy({ it.value.first.row }, { it.value.first.col }))
        .forEach { (id, ps) ->
            val (p, s) = ps
            val sp = GridSpan(
                s.cols.coerceIn(1, columns),
                s.rows.coerceAtLeast(1),
            )
            var pos = GridPos(
                p.col.coerceIn(0, columns - sp.cols),
                p.row.coerceAtLeast(0),
            )
            while (overlaps(placed.values, pos, sp)) {
                pos = pos.copy(row = pos.row + 1)
            }
            placed[id] = pos to sp
        }
    return placed.mapValues { it.value.first }
}
