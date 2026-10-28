package com.vibiate.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.withFrameNanos
import com.vibiate.launcher.R
import com.vibiate.launcher.data.WidgetEntry
import com.vibiate.launcher.ui.components.toImageBitmap
import com.vibiate.launcher.ui.theme.AviateBlue
import com.vibiate.launcher.ui.theme.AviateOrange
import com.vibiate.launcher.ui.theme.LocalAviateColors
import com.vibiate.launcher.ui.theme.LocalIsDark
import com.vibiate.launcher.widget.LongPressAppWidgetHostView
import com.vibiate.launcher.widget.GridPos
import com.vibiate.launcher.widget.GridSpan
import com.vibiate.launcher.widget.WIDGET_COLUMNS
import com.vibiate.launcher.widget.WidgetController
import com.vibiate.launcher.widget.autoSpan
import com.vibiate.launcher.widget.clampSpan
import com.vibiate.launcher.widget.flowLayout
import com.vibiate.launcher.widget.moved
import com.vibiate.launcher.widget.pxToCells
import kotlin.math.roundToInt

private val cellHeight = 96.dp
private val gridGap = 8.dp
private val gridPadding = 12.dp
private val titleHeight = 20.dp
private val barHeight = 52.dp
private val barGap = 4.dp

/**
 * Widget space: bound widgets flow on a 4-column cell grid directly on the
 * wallpaper. Long-press opens a context menu (resize / rename / remove);
 * adding widgets lives in the Customize sheet.
 *
 * [screenId] 0 is the built-in Widgets page; custom spaces pass their
 * CustomScreen id and show a [screenName] header with a remove affordance.
 * [onEditingChange] reports whether a widget is being resized so the pager
 * can suspend horizontal scrolling during handle drags.
 */
@Composable
fun WidgetsScreen(
    controller: WidgetController,
    screenId: Int,
    screenName: String? = null,
    onRemoveScreen: (() -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    showEmptyState: Boolean = true,
    onEditingChange: (Boolean) -> Unit = {},
) {
    val allWidgets by controller.widgets.collectAsState()
    val widgets = allWidgets.filter { it.screenId == screenId }
    val density = LocalDensity.current
    val aviate = LocalAviateColors.current
    // Same rule as Home: shadow only in dark mode keeps text legible over
    // bright wallpaper; light wallpapers get dark text and no shadow.
    val textShadow: Shadow? = if (LocalIsDark.current) {
        Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
    } else {
        null
    }

    var confirmRemove by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<Int?>(null) }
    var renameId by remember { mutableStateOf<Int?>(null) }
    var resizeId by remember { mutableStateOf<Int?>(null) }
    var previewSpan by remember { mutableStateOf<GridSpan?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // Rearrange mode (category-style): widgets collapse to bars while one is
    // dragged to a new position.
    var rearrangeDragId by remember { mutableStateOf<Int?>(null) }
    var rearrangeOrder by remember { mutableStateOf<List<Int>?>(null) }
    var fingerRaw by remember { mutableStateOf(Offset.Zero) }
    var fingerLocal by remember { mutableStateOf(Offset.Zero) }
    var gridScreenPos by remember { mutableStateOf(Offset.Zero) }
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportBottom by remember { mutableStateOf(0f) }
    val scroll = rememberScrollState()
    val rearranging = rearrangeDragId != null
    val rootView = LocalView.current
    val windowOrigin = remember {
        val loc = IntArray(2)
        rootView.getLocationOnScreen(loc)
        Offset(loc[0].toFloat(), loc[1].toFloat())
    }
    val orderedWidgets = rearrangeOrder?.mapNotNull { id ->
        widgets.firstOrNull { it.appWidgetId == id }
    } ?: widgets

    // Drop resize/menu state when the target widget disappears.
    val effectiveResizeId = resizeId?.takeIf { id ->
        widgets.any { it.appWidgetId == id }
    }
    SideEffect { if (resizeId != effectiveResizeId) resizeId = effectiveResizeId }
    SideEffect { onEditingChange(effectiveResizeId != null || rearranging) }

    // Edge auto-scroll while rearranging (same 64dp rule as Collections).
    LaunchedEffect(rearranging) {
        val edge = with(density) { 64.dp.toPx() }
        while (rearranging) {
            when {
                fingerRaw.y < viewportTop + edge -> scroll.scrollBy(-18f)
                fingerRaw.y > viewportBottom - edge -> scroll.scrollBy(18f)
            }
            withFrameNanos {}
        }
    }

    BackHandler(enabled = effectiveResizeId != null) {
        resizeId = null
        previewSpan = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .onGloballyPositioned {
                viewportTop = (it.positionInWindow() + windowOrigin).y
                viewportBottom = viewportTop + it.size.height
            }
            .padding(horizontal = gridPadding)
            // Tap outside a widget exits resize mode. The detector is only
            // attached while resizing: an always-on detectTapGestures would
            // consume the down event and starve the parent's long-press
            // (wallpaper long-press -> Customize). Host views swallow their
            // own taps, so tapping a widget never reaches this anyway.
            .then(
                if (effectiveResizeId != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            resizeId = null
                            previewSpan = null
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        if (screenName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = screenName,
                    color = aviate.homeText,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    style = LocalTextStyle.current.copy(shadow = textShadow),
                )
                Spacer(Modifier.weight(1f))
                if (onRemoveScreen != null) {
                    IconButton(onClick = { confirmRemove = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.remove_space),
                            tint = aviate.homeText,
                        )
                    }
                }
            }
        }
        header?.invoke(this)

        if (widgets.isEmpty() && showEmptyState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.no_widgets),
                    color = aviate.homeText,
                    fontSize = 16.sp,
                    style = LocalTextStyle.current.copy(shadow = textShadow),
                )
                Text(
                    text = stringResource(R.string.widget_add_hint),
                    color = aviate.homeTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    style = LocalTextStyle.current.copy(shadow = textShadow),
                )
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cellW = (maxWidth - gridGap * (WIDGET_COLUMNS - 1)) /
                    WIDGET_COLUMNS
                val infos = orderedWidgets.associate {
                    it.appWidgetId to controller.providerInfo(it.appWidgetId)
                }
                val spans = orderedWidgets.map { e -> effectiveSpan(e, infos[e.appWidgetId], density.density, cellW) }
                val layoutSpans = orderedWidgets.mapIndexed { i, e ->
                    if (e.appWidgetId == effectiveResizeId) previewSpan ?: spans[i]
                    else spans[i]
                }

                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            gridScreenPos = it.positionInWindow() + windowOrigin
                        },
                    content = {
                        orderedWidgets.forEach { entry ->
                            key(entry.appWidgetId) {
                                WidgetCell(
                                    entry = entry,
                                    info = infos[entry.appWidgetId],
                                    resizing = entry.appWidgetId == effectiveResizeId,
                                    dragging = dragging,
                                    cellW = cellW,
                                    controller = controller,
                                    textShadow = textShadow,
                                    menuOpen = menuId == entry.appWidgetId,
                                    onMenuOpen = {
                                        // Long-press exits resize mode first.
                                        resizeId = null
                                        previewSpan = null
                                        menuId = entry.appWidgetId
                                    },
                                    onMenuDismiss = { menuId = null },
                                    onResize = {
                                        menuId = null
                                        resizeId = entry.appWidgetId
                                        previewSpan = null
                                    },
                                    onRename = {
                                        menuId = null
                                        renameId = entry.appWidgetId
                                    },
                                    onRemove = {
                                        menuId = null
                                        controller.removeWidget(entry.appWidgetId)
                                    },
                                    onDragStart = {
                                        menuId = null
                                        resizeId = null
                                        previewSpan = null
                                        rearrangeOrder =
                                            widgets.map { it.appWidgetId }
                                        rearrangeDragId = entry.appWidgetId
                                    },
                                    onDragMove = { rx, ry ->
                                        fingerRaw = Offset(rx, ry)
                                        fingerLocal = Offset(
                                            rx - gridScreenPos.x,
                                            ry - gridScreenPos.y,
                                        )
                                        val order = rearrangeOrder
                                        val dragId = rearrangeDragId
                                        if (order != null && dragId != null) {
                                            val others = order.filter {
                                                it != dragId
                                            }
                                            val barPx = with(density) {
                                                (barHeight + barGap).toPx()
                                            }
                                            val halfBar = with(density) {
                                                barHeight.toPx() / 2f
                                            }
                                            // Count other bars whose center is
                                            // above the finger (category rule).
                                            val insert = others.indices.count {
                                                i -> i * barPx + halfBar <
                                                    fingerLocal.y
                                            }
                                            val cur = order.indexOf(dragId)
                                            if (cur >= 0 &&
                                                insert != cur &&
                                                insert <= others.size
                                            ) {
                                                rearrangeOrder =
                                                    order.moved(cur, insert)
                                            }
                                        }
                                    },
                                    onDragEnd = { cancelled ->
                                        val order = rearrangeOrder
                                        if (!cancelled && order != null &&
                                            rearrangeDragId != null
                                        ) {
                                            controller.moveWidgets(
                                                screenId, order,
                                            )
                                        }
                                        rearrangeDragId = null
                                        rearrangeOrder = null
                                    },
                                    rearranging = rearranging,
                                    onPreviewSpan = { previewSpan = it },
                                    onDraggingChange = { dragging = it },
                                    onResizeDone = { span ->
                                        // Stay in resize mode after a release:
                                        // only tap-outside / Back / page
                                        // switch exits it.
                                        dragging = false
                                        previewSpan = null
                                        controller.resizeWidget(
                                            entry.appWidgetId, span.cols, span.rows,
                                        )
                                    },
                                )
                            }
                        }
                        if (rearranging) {
                            // Cyan placeholder marks the insert slot.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .border(
                                        2.dp, AviateBlue,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                            // Ghost bar following the finger.
                            rearrangeDragId?.let { dragId ->
                                widgets.firstOrNull {
                                    it.appWidgetId == dragId
                                }?.let { e ->
                                    val (label, icon) =
                                        barVisual(e, infos[dragId])
                                    WidgetBar(
                                        label = label,
                                        icon = icon,
                                        ghost = true,
                                    )
                                }
                            }
                        }
                    },
                ) { measurables, constraints ->
                    if (rearranging) {
                        // Collapsed bar list: cells keep their AndroidViews
                        // composed (measured 0×0 for the dragged one) so the
                        // gesture's host view is never detached mid-drag.
                        val barPx = barHeight.roundToPx()
                        val bGapPx = barGap.roundToPx()
                        val w = constraints.maxWidth
                        val n = orderedWidgets.size
                        val placeables = measurables.mapIndexed { i, m ->
                            when {
                                i >= n -> m.measure(
                                    Constraints.fixed(w, barPx),
                                )
                                orderedWidgets[i].appWidgetId == rearrangeDragId ->
                                    m.measure(Constraints.fixed(0, 0))
                                else -> m.measure(
                                    Constraints.fixed(w, barPx),
                                )
                            }
                        }
                        layout(w, n * (barPx + bGapPx)) {
                            var slot = 0
                            orderedWidgets.forEachIndexed { i, e ->
                                val y = slot * (barPx + bGapPx)
                                if (e.appWidgetId == rearrangeDragId) {
                                    // [n] = placeholder at the insert slot.
                                    placeables[n].place(0, y)
                                } else {
                                    placeables[i].place(0, y)
                                }
                                slot++
                            }
                            val ghostY = (fingerLocal.y - barPx / 2f)
                                .roundToInt()
                                .coerceIn(
                                    0,
                                    (n * (barPx + bGapPx) - barPx)
                                        .coerceAtLeast(0),
                                )
                            placeables[n + 1].place(0, ghostY, zIndex = 2f)
                        }
                    } else {
                    val gapPx = gridGap.roundToPx()
                    val cellWPx = cellW.roundToPx()
                    val cellHPx = cellHeight.roundToPx()
                    val positions: List<GridPos> = flowLayout(layoutSpans)
                    val placeables = measurables.mapIndexed { i, m ->
                        val s = layoutSpans[i]
                        m.measure(
                            Constraints.fixed(
                                s.cols * cellWPx + (s.cols - 1) * gapPx,
                                s.rows * cellHPx + (s.rows - 1) * gapPx,
                            )
                        )
                    }
                    val usedRows = positions.indices.maxOfOrNull {
                        positions[it].row + layoutSpans[it].rows
                    } ?: 0
                    val totalH = if (usedRows == 0) 0
                        else usedRows * cellHPx + (usedRows - 1) * gapPx
                    layout(constraints.maxWidth, totalH) {
                        placeables.forEachIndexed { i, p ->
                            p.place(
                                positions[i].col * (cellWPx + gapPx),
                                positions[i].row * (cellHPx + gapPx),
                            )
                        }
                    }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ---- Widget context menu's dialogs ----

    renameId?.let { id ->
        val entry = widgets.firstOrNull { it.appWidgetId == id }
        if (entry == null) {
            renameId = null
        } else {
            var text by rememberSaveable(id) { mutableStateOf(entry.title) }
            AlertDialog(
                onDismissRequest = { renameId = null },
                title = { Text(stringResource(R.string.widget_rename_title)) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(40) },
                        singleLine = true,
                        placeholder = { Text(entry.labelPlaceholder()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        renameId = null
                        controller.renameWidget(id, text)
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (confirmRemove && screenName != null && onRemoveScreen != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_space_title, screenName)) },
            text = { Text(stringResource(R.string.remove_space_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemoveScreen()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun WidgetEntry.labelPlaceholder(): String = providerClass
    .substringAfterLast('.')

/** Stored span if set, else the provider's preferred span on the grid. */
private fun effectiveSpan(
    entry: WidgetEntry,
    info: AppWidgetProviderInfo?,
    density: Float,
    cellW: Dp,
): GridSpan {
    if (entry.cols > 0 && entry.rows > 0) {
        return GridSpan(
            entry.cols.coerceIn(1, WIDGET_COLUMNS),
            entry.rows.coerceIn(1, 8),
        )
    }
    val targetCols = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        info?.targetCellWidth ?: 0
    } else 0
    val targetRows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        info?.targetCellHeight ?: 0
    } else 0
    return autoSpan(
        minWidthDp = (info?.minWidth ?: 0) / density,
        minHeightDp = (info?.minHeight ?: 0) / density,
        targetCols = targetCols,
        targetRows = targetRows,
        cellWidthDp = cellW.value,
        cellHeightDp = cellHeight.value,
    )
}

/**
 * One grid cell: the widget host view (with its title label), a blue frame +
 * drag handles while resizing, and the long-press context menu.
 */
@Composable
private fun WidgetCell(
    entry: WidgetEntry,
    info: AppWidgetProviderInfo?,
    resizing: Boolean,
    rearranging: Boolean,
    dragging: Boolean,
    cellW: Dp,
    controller: WidgetController,
    textShadow: Shadow?,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onResize: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onPreviewSpan: (GridSpan) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onResizeDone: (GridSpan) -> Unit,
) {
    val density = LocalDensity.current
    val cellWPx = with(density) { (cellW + gridGap).toPx() }
    val cellHPx = with(density) { (cellHeight + gridGap).toPx() }
    var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
    var reportedSize by remember { mutableStateOf<IntSize?>(null) }

    val horizontalResizable = info == null ||
        info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val verticalResizable = info == null ||
        info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    val resizable = horizontalResizable || verticalResizable
    // Stored span is the drag base; fall back to the provider's default.
    val baseSpan = effectiveSpan(entry, info, density.density, cellW)
    val minCols = info?.let { pxToCells(it.minResizeWidth, density.density, cellW.value) }
        ?.coerceAtLeast(1) ?: 1
    val minRows = info?.let { pxToCells(it.minResizeHeight, density.density, cellHeight.value) }
        ?.coerceAtLeast(1) ?: 1
    val maxCols = info
        ?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.maxResizeWidth else 0
        }
        ?.takeIf { it > 0 }
        ?.let { pxToCells(it, density.density, cellW.value) }
        ?: WIDGET_COLUMNS
    val maxRows = info
        ?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.maxResizeHeight else 0
        }
        ?.takeIf { it > 0 }
        ?.let { pxToCells(it, density.density, cellHeight.value) }
        ?: 8

    /** Candidate span after [acc] px of accumulated drag on a handle. */
    fun dragSpan(acc: Offset, cols: Boolean, rows: Boolean): GridSpan = clampSpan(
        GridSpan(
            if (cols) baseSpan.cols + (acc.x / cellWPx).roundToInt()
            else baseSpan.cols,
            if (rows) baseSpan.rows + (acc.y / cellHPx).roundToInt()
            else baseSpan.rows,
        ),
        minCols = minCols, minRows = minRows,
        maxCols = maxCols, maxRows = maxRows,
        horizontalResizable = cols && horizontalResizable,
        verticalResizable = rows && verticalResizable,
        original = baseSpan,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (rearranging) {
                val (label, icon) = barVisual(entry, info)
                WidgetBar(label = label, icon = icon, ghost = false)
            } else if (entry.title.isNotEmpty()) {
                Text(
                    text = entry.title.uppercase(),
                    color = LocalAviateColors.current.homeText,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(shadow = textShadow),
                    modifier = Modifier.height(titleHeight),
                )
            }
            Box(
                // Rearrange keeps the same AndroidView composed at 1dp (not
                // 0: a 0-size AndroidView detaches from the window, which
                // kills an in-flight drag stream and forces re-inflation).
                if (rearranging) Modifier.size(1.dp)
                else Modifier.weight(1f).fillMaxWidth(),
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = controller.createView(entry)
                            ?: android.widget.FrameLayout(ctx)
                        hostView = view as? AppWidgetHostView
                        view
                    },
                    // update runs every recomposition, so the host view always
                    // calls the current callbacks (the factory assignment
                    // would freeze the lambdas from first composition).
                    update = { view ->
                        (view as? LongPressAppWidgetHostView)?.let { v ->
                            v.onLongPress = onMenuOpen
                            v.onDragStart = onDragStart
                            v.onDragMove = onDragMove
                            v.onDragEnd = onDragEnd
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            // Report once per size; the widget then picks the
                            // matching responsive layout. Skip while collapsed
                            // so providers don't get a 0-size update.
                            if (rearranging) return@onSizeChanged
                            val view = hostView ?: return@onSizeChanged
                            if (reportedSize == size) return@onSizeChanged
                            reportedSize = size
                            val wDp = size.width / density.density
                            val hDp = size.height / density.density
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                view.updateAppWidgetSize(
                                    Bundle(),
                                    listOf(SizeF(wDp, hDp)),
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                view.updateAppWidgetSize(
                                    null,
                                    info?.let {
                                        (it.minWidth / density.density).toInt()
                                    } ?: wDp.toInt(),
                                    info?.let {
                                        (it.minHeight / density.density).toInt()
                                    } ?: hDp.toInt(),
                                    wDp.toInt(),
                                    hDp.toInt(),
                                )
                            }
                        },
                )
                // Resize affordance: blue frame + translucent preview tint
                // while a handle is being dragged.
                if (resizing) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .border(2.dp, AviateBlue)
                            .then(
                                if (dragging) {
                                    Modifier.background(
                                        AviateBlue.copy(alpha = 0.25f)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.widget_resize))
                                if (!resizable) {
                                    Text(
                                        stringResource(R.string.widget_cannot_resize),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        enabled = resizable,
                        onClick = onResize,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.widget_rename)) },
                        onClick = onRename,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.remove_widget),
                                color = AviateOrange,
                            )
                        },
                        onClick = onRemove,
                    )
                }
            }
        }

        if (resizing) {
            // Edge handles: 48dp touch target, 20dp white dot ringed blue.
            if (horizontalResizable) {
                ResizeHandle(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onPreview = { acc ->
                        onPreviewSpan(dragSpan(acc, cols = true, rows = false))
                    },
                    onDraggingChange = onDraggingChange,
                    onDone = { acc ->
                        onResizeDone(dragSpan(acc, cols = true, rows = false))
                    },
                )
            }
            if (verticalResizable) {
                ResizeHandle(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPreview = { acc ->
                        onPreviewSpan(dragSpan(acc, cols = false, rows = true))
                    },
                    onDraggingChange = onDraggingChange,
                    onDone = { acc ->
                        onResizeDone(dragSpan(acc, cols = false, rows = true))
                    },
                )
            }
            if (horizontalResizable && verticalResizable) {
                ResizeHandle(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onPreview = { acc ->
                        onPreviewSpan(dragSpan(acc, cols = true, rows = true))
                    },
                    onDraggingChange = onDraggingChange,
                    onDone = { acc ->
                        onResizeDone(dragSpan(acc, cols = true, rows = true))
                    },
                )
            }
        }
    }
}

/** 48dp touch target with a 20dp white dot ringed in Aviate blue. */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onPreview: (Offset) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDone: (Offset) -> Unit,
) {
    // pointerInput(Unit) freezes the lambdas captured at first composition;
    // updated-state refs keep each drag computing from the widget's current
    // span and constraints after a resize recomposition.
    val preview by rememberUpdatedState(onPreview)
    val dragChange by rememberUpdatedState(onDraggingChange)
    val done by rememberUpdatedState(onDone)
    Box(
        modifier
            .size(48.dp)
            .pointerInput(Unit) {
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        acc = Offset.Zero
                        dragChange(true)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        acc += amount
                        preview(acc)
                    },
                    onDragEnd = { done(acc) },
                    onDragCancel = {
                        acc = Offset.Zero
                        done(Offset.Zero)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, AviateBlue, CircleShape),
        )
    }
}

/** Label + provider icon for a widget's rearrange bar. */
@Composable
private fun barVisual(
    entry: WidgetEntry,
    info: AppWidgetProviderInfo?,
): Pair<String, ImageBitmap?> {
    val ctx = LocalContext.current
    val label = entry.title.ifEmpty {
        info?.loadLabel(ctx.packageManager)
            ?: entry.providerClass.substringAfterLast('.')
    }
    val icon = remember(entry.appWidgetId, info) {
        runCatching {
            info?.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi)
                ?.toImageBitmap(48)
        }.getOrNull()
    }
    return label to icon
}

/** Compact title bar a widget collapses to while rearranging. */
@Composable
private fun WidgetBar(label: String, icon: ImageBitmap?, ghost: Boolean) {
    // Mirrors the collapsed category card and its drag ghost.
    val aviate = LocalAviateColors.current
    val shape = RoundedCornerShape(2.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
            .then(if (ghost) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(
                if (ghost) {
                    // Opaque, or the elevation shadow shows through.
                    aviate.cardBackground.compositeOver(
                        if (LocalIsDark.current) Color(0xFF252628) else Color.White
                    )
                } else {
                    aviate.cardBackground
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(14.dp))
        }
        Text(
            text = label.uppercase(),
            color = aviate.cardTitle,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
