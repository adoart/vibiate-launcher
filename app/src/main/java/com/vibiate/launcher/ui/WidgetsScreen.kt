package com.vibiate.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.positionInRoot
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
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.PageShortcut
import com.vibiate.launcher.data.WidgetEntry
import com.vibiate.launcher.data.dockKey
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import com.vibiate.launcher.ui.components.AppIcon
import com.vibiate.launcher.ui.components.openAppDetails
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.LocalDragDrop
import com.vibiate.launcher.ui.drag.trackBounds
import com.vibiate.launcher.ui.theme.LocalAviateColors
import com.vibiate.launcher.ui.theme.LocalIsDark
import com.vibiate.launcher.widget.LongPressAppWidgetHostView
import com.vibiate.launcher.widget.GridPos
import com.vibiate.launcher.widget.GridSpan
import com.vibiate.launcher.widget.WIDGET_COLUMNS
import com.vibiate.launcher.widget.WidgetController
import com.vibiate.launcher.widget.autoSpan
import com.vibiate.launcher.widget.clampSpan
import com.vibiate.launcher.widget.dropPlacement
import com.vibiate.launcher.widget.firstFreeSpot
import com.vibiate.launcher.widget.flowLayout
import com.vibiate.launcher.widget.placeWithPushDown
import com.vibiate.launcher.widget.pxToCells
import com.vibiate.launcher.widget.shortcutDropId
import com.vibiate.launcher.widget.shortcutGridId
import kotlin.math.max
import kotlin.math.roundToInt

private val cellHeight = 96.dp
private val gridGap = 8.dp
private val gridPadding = 12.dp
private val titleHeight = 20.dp
private val editCorner = 20.dp
private val editShape = RoundedCornerShape(editCorner)

/**
 * Widget space: bound widgets flow on a 4-column cell grid directly on the
 * wallpaper. Long-press selects a widget for editing and opens a context menu (rename / remove);
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
    /** Installed apps — used to render + drop page shortcuts. */
    apps: List<AppEntry> = emptyList(),
    iconTheme: IconTheme? = null,
    onLaunch: (AppEntry) -> Unit = {},
    isCurrentPage: Boolean = true,
    /** Remove a dock icon that was moved onto this page. */
    onDockRemove: (String) -> Unit = {},
    header: (@Composable ColumnScope.() -> Unit)? = null,
    showEmptyState: Boolean = true,
    /** 0..1 background-blur fraction while a widget is edited (OxygenOS). */
    editBlur: Float = 0f,
    onEditingChange: (Boolean) -> Unit = {},
) {
    val allWidgets by controller.widgets.collectAsState()
    val widgets = allWidgets.filter { it.screenId == screenId }
    val allShortcuts by controller.shortcuts.collectAsState()
    val screenShortcuts = allShortcuts.filter { it.screenId == screenId }
    // Only renderable shortcuts take part in the grid (app still installed).
    val shortcutCells = screenShortcuts.mapNotNull { s ->
        apps.firstOrNull { it.componentKey == s.componentKey }
            ?.let { s to it }
    }
    val drag = LocalDragDrop.current
    val pageKey = "page_$screenId"
    var gridRoot by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuId by remember { mutableStateOf<Int?>(null) }
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
    // Edit mode: one widget selected — blue frame + handles; dragging a
    // handle resizes, dragging the body moves/reorders it.
    var editId by remember { mutableStateOf<Int?>(null) }
    var previewSpan by remember { mutableStateOf<GridSpan?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // Move: the dragged widget floats under the finger (translucent +
    // dimmed), a slot marks its target grid position, colliders push down.
    var movingId by remember { mutableStateOf<Int?>(null) }
    var movePreview by remember { mutableStateOf<Map<Int, GridPos>?>(null) }
    var dragStartItems by remember {
        mutableStateOf<Map<Int, Pair<GridPos, GridSpan>>?>(null)
    }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var fingerRaw by remember { mutableStateOf(Offset.Zero) }
    var gridScreenPos by remember { mutableStateOf(Offset.Zero) }
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportBottom by remember { mutableStateOf(0f) }
    val scroll = rememberScrollState()
    val moving = movingId != null
    val rootView = LocalView.current
    val windowOrigin = remember {
        val loc = IntArray(2)
        rootView.getLocationOnScreen(loc)
        Offset(loc[0].toFloat(), loc[1].toFloat())
    }
    val orderedWidgets = widgets

    // Drop edit/menu state when the target widget disappears.
    val effectiveEditId = editId?.takeIf { id ->
        widgets.any { it.appWidgetId == id }
    }
    SideEffect { if (editId != effectiveEditId) editId = effectiveEditId }
    SideEffect { onEditingChange(effectiveEditId != null || moving) }

    // Edge auto-scroll while moving (same 64dp rule as Collections).
    LaunchedEffect(moving) {
        val edge = with(density) { 64.dp.toPx() }
        while (moving) {
            when {
                fingerRaw.y < viewportTop + edge -> scroll.scrollBy(-18f)
                fingerRaw.y > viewportBottom - edge -> scroll.scrollBy(18f)
            }
            withFrameNanos {}
        }
    }

    BackHandler(enabled = effectiveEditId != null || moving) {
        editId = null
        previewSpan = null
        movingId = null
        movePreview = null
        dragStartItems = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .onGloballyPositioned {
                viewportTop = (it.positionInWindow() + windowOrigin).y
                viewportBottom = viewportTop + it.size.height
            }
            .trackBounds(pageKey)
            .padding(horizontal = gridPadding)
            // Tap outside a widget exits edit mode. The detector is only
            // attached while editing: an always-on detectTapGestures would
            // consume the down event and starve the parent's long-press
            // (wallpaper long-press -> Customize). Host views swallow their
            // own taps, so tapping a widget never reaches this anyway.
            .then(
                if (effectiveEditId != null || moving) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            editId = null
                            previewSpan = null
                            movingId = null
                            movePreview = null
                            dragStartItems = null
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

        if (widgets.isEmpty() && shortcutCells.isEmpty() &&
            !drag.isDragging && showEmptyState
        ) {
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
                    if (e.appWidgetId == effectiveEditId) previewSpan ?: spans[i]
                    else spans[i]
                }
                val gapPxF = with(density) { gridGap.toPx() }
                val cellWPx = with(density) { cellW.toPx() }
                val cellHPx = with(density) { cellHeight.toPx() }
                val stepX = cellWPx + gapPxF
                val stepY = cellHPx + gapPxF

                // Persist fixed grid positions. With nothing placed yet the
                // flow layout becomes the positions (existing pages look
                // identical); later additions fill the first free spot.
                // Shortcut ids join the map as -id with a 1x1 span.
                LaunchedEffect(orderedWidgets, screenShortcuts, cellW) {
                    val unplacedW = orderedWidgets.filter {
                        it.col < 0 || it.row < 0
                    }
                    val unplacedS = screenShortcuts.filter {
                        it.col < 0 || it.row < 0
                    }
                    if (unplacedW.isEmpty() && unplacedS.isEmpty()) {
                        return@LaunchedEffect
                    }
                    val spanOf = orderedWidgets.associate {
                        it.appWidgetId to effectiveSpan(
                            it, infos[it.appWidgetId], density.density, cellW,
                        )
                    }
                    val positions: Map<Int, GridPos> =
                        if (unplacedW.size == orderedWidgets.size &&
                            unplacedS.size == screenShortcuts.size
                        ) {
                            val fl = flowLayout(
                                orderedWidgets.map { spanOf[it.appWidgetId]!! } +
                                    screenShortcuts.map { GridSpan(1, 1) }
                            )
                            (orderedWidgets.mapIndexed { i, e ->
                                e.appWidgetId to fl[i]
                            } + screenShortcuts.mapIndexed { i, s ->
                                shortcutGridId(s.id) to
                                    fl[orderedWidgets.size + i]
                            }).toMap()
                        } else {
                            val m = LinkedHashMap<Int, Pair<GridPos, GridSpan>>()
                            orderedWidgets
                                .filter { it.col >= 0 && it.row >= 0 }
                                .forEach {
                                    m[it.appWidgetId] = GridPos(it.col, it.row) to
                                        spanOf[it.appWidgetId]!!
                                }
                            screenShortcuts
                                .filter { it.col >= 0 && it.row >= 0 }
                                .forEach {
                                    m[shortcutGridId(it.id)] =
                                        GridPos(it.col, it.row) to GridSpan(1, 1)
                                }
                            unplacedW.forEach { e ->
                                val sp = spanOf[e.appWidgetId]!!
                                m[e.appWidgetId] =
                                    firstFreeSpot(m.values.toList(), sp) to sp
                            }
                            unplacedS.forEach { s ->
                                m[shortcutGridId(s.id)] =
                                    firstFreeSpot(m.values.toList(), GridSpan(1, 1)) to
                                        GridSpan(1, 1)
                            }
                            m.mapValues { it.value.first }
                        }
                    controller.placeWidgets(screenId, positions)
                }

                // Rendered positions: stored when placed, flow order for the
                // one frame before migration lands; previews while editing.
                val flowFallback = flowLayout(
                    layoutSpans + shortcutCells.map { GridSpan(1, 1) }
                )
                val basePos = mutableMapOf<Int, GridPos>()
                orderedWidgets.forEachIndexed { i, e ->
                    basePos[e.appWidgetId] = if (e.col >= 0 && e.row >= 0) {
                        GridPos(e.col, e.row)
                    } else {
                        flowFallback[i]
                    }
                }
                shortcutCells.forEachIndexed { i, cell ->
                    val s = cell.first
                    basePos[shortcutGridId(s.id)] =
                        if (s.col >= 0 && s.row >= 0) {
                            GridPos(s.col, s.row)
                        } else {
                            flowFallback[orderedWidgets.size + i]
                        }
                }
                val baseItems = orderedWidgets.associate { e ->
                    e.appWidgetId to
                        (basePos[e.appWidgetId]!! to spans[orderedWidgets.indexOf(e)])
                } + shortcutCells.associate { cell ->
                    val s = cell.first
                    shortcutGridId(s.id) to
                        (basePos[shortcutGridId(s.id)]!! to GridSpan(1, 1))
                }

                // ---- App-icon drop target: cell under the finger ----
                val overDock by derivedStateOf {
                    drag.rects["dock"]?.contains(drag.pointer) == true ||
                        drag.rects["dock_remove"]?.contains(drag.pointer) == true
                }
                val dropTarget by derivedStateOf {
                    val p = drag.payload
                    if ((p !is DragPayload.App && p !is DragPayload.Dock &&
                            p !is DragPayload.Shortcut) ||
                        !isCurrentPage || overDock ||
                        drag.rects[pageKey]?.contains(drag.pointer) != true
                    ) {
                        null
                    } else {
                        GridPos(
                            ((drag.pointer.x - gridRoot.x) / stepX)
                                .toInt().coerceIn(0, WIDGET_COLUMNS - 1),
                            ((drag.pointer.y - gridRoot.y) / stepY)
                                .toInt().coerceAtLeast(0),
                        )
                    }
                }
                val pendingDrop by derivedStateOf {
                    val target = dropTarget ?: return@derivedStateOf null
                    val p = drag.payload
                    val nextId = (allShortcuts.maxOfOrNull { it.id } ?: 0) + 1
                    when (p) {
                        is DragPayload.App -> {
                            val same = screenShortcuts.firstOrNull {
                                it.componentKey == p.app.componentKey
                            }?.id
                            val gid = -shortcutDropId(same, null, nextId)
                            PendingDrop(
                                dropPlacement(baseItems, gid, target),
                                p.app.componentKey, null,
                            )
                        }
                        is DragPayload.Dock -> {
                            val key = p.entry.component?.let { cn ->
                                apps.firstOrNull {
                                    it.componentKey == dockKey(cn)
                                }?.componentKey
                            } ?: return@derivedStateOf null
                            val same = screenShortcuts.firstOrNull {
                                it.componentKey == key
                            }?.id
                            val gid = -shortcutDropId(same, null, nextId)
                            PendingDrop(
                                dropPlacement(baseItems, gid, target),
                                key, null,
                            )
                        }
                        is DragPayload.Shortcut -> {
                            val same = screenShortcuts.firstOrNull {
                                it.componentKey == p.app.componentKey &&
                                    it.id != p.shortcut.id
                            }?.id
                            val gid = -shortcutDropId(
                                same, p.shortcut.id, nextId,
                            )
                            PendingDrop(
                                dropPlacement(baseItems, gid, target),
                                p.app.componentKey, p.shortcut.id,
                            )
                        }
                        else -> null
                    }
                }
                val dropPreview = pendingDrop?.positions

                // This page's drop zone — wins only while visible, inside its
                // rect, and not over the dock/remove zones.
                val latestPending by rememberUpdatedState(pendingDrop)
                val latestCurrent by rememberUpdatedState(isCurrentPage)
                DisposableEffect(drag) {
                    val handler: (DragPayload, Offset) -> Boolean = handler@{ p, _ ->
                        if (!latestCurrent || latestPending == null) {
                            false
                        } else {
                            val drop = latestPending!!
                            when (p) {
                                is DragPayload.App -> {
                                    controller.dropShortcut(
                                        screenId, drop.componentKey,
                                        drop.positions, drop.movingShortcutId,
                                    )
                                    true
                                }
                                is DragPayload.Dock -> {
                                    controller.dropShortcut(
                                        screenId, drop.componentKey,
                                        drop.positions, drop.movingShortcutId,
                                    )
                                    p.entry.component?.let {
                                        onDockRemove(dockKey(it))
                                    }
                                    true
                                }
                                is DragPayload.Shortcut -> {
                                    controller.dropShortcut(
                                        screenId, drop.componentKey,
                                        drop.positions, drop.movingShortcutId,
                                    )
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                    drag.dropHandlers[pageKey] = handler
                    onDispose {
                        if (drag.dropHandlers[pageKey] === handler) {
                            drag.dropHandlers.remove(pageKey)
                        }
                    }
                }

                // Growing a resized widget pushes overlapped widgets down.
                val resizePreview = previewSpan?.let { ps ->
                    val id = effectiveEditId ?: return@let null
                    placeWithPushDown(
                        baseItems, id, basePos[id] ?: GridPos(0, 0), ps,
                    )
                }
                val livePos =
                    movePreview ?: resizePreview ?: dropPreview ?: basePos

                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            gridScreenPos =
                                it.positionInWindow() + windowOrigin
                            gridRoot = it.positionInRoot()
                        },
                    content = {
                        orderedWidgets.forEach { entry ->
                            key(entry.appWidgetId) {
                                WidgetCell(
                                    entry = entry,
                                    info = infos[entry.appWidgetId],
                                    editing = entry.appWidgetId == effectiveEditId,
                                    contentBlur = editBlur > 0.01f &&
                                        entry.appWidgetId != effectiveEditId &&
                                        entry.appWidgetId != movingId,
                                    dragging = dragging,
                                    cellW = cellW,
                                    controller = controller,
                                    textShadow = textShadow,
                                    menuOpen = menuId == entry.appWidgetId,
                                    onMenuOpen = {
                                        // Long-press selects the widget for
                                        // editing AND opens the menu.
                                        editId = entry.appWidgetId
                                        previewSpan = null
                                        movingId = null
                                        movePreview = null
                                        dragStartItems = null
                                        menuId = entry.appWidgetId
                                    },
                                    // Dismissing the menu keeps the widget
                                    // selected for editing.
                                    onMenuDismiss = { menuId = null },
                                    onRename = {
                                        menuId = null
                                        editId = null
                                        renameId = entry.appWidgetId
                                    },
                                    onRemove = {
                                        menuId = null
                                        controller.removeWidget(entry.appWidgetId)
                                    },
                                    onDragStart = { dx, dy ->
                                        menuId = null
                                        editId = entry.appWidgetId
                                        previewSpan = null
                                        fingerRaw = Offset(dx, dy)
                                        // grabOffset = down point relative to
                                        // the widget's grid-local top-left.
                                        val gp = basePos[entry.appWidgetId]
                                        grabOffset = if (gp != null) {
                                            Offset(
                                                dx - gridScreenPos.x -
                                                    gp.col * stepX,
                                                dy - gridScreenPos.y -
                                                    gp.row * stepY,
                                            )
                                        } else Offset.Zero
                                        dragStartItems = baseItems
                                        movePreview = null
                                        movingId = entry.appWidgetId
                                    },
                                    onDragMove = { rx, ry ->
                                        fingerRaw = Offset(rx, ry)
                                        val items = dragStartItems
                                        val dragId = movingId
                                        if (items != null && dragId != null) {
                                            val span = items[dragId]!!.second
                                            // Target cell = ghost top-left
                                            // rounded to the nearest cell.
                                            val target = GridPos(
                                                ((rx - gridScreenPos.x -
                                                    grabOffset.x) / stepX)
                                                    .roundToInt()
                                                    .coerceIn(
                                                        0,
                                                        WIDGET_COLUMNS -
                                                            span.cols,
                                                    ),
                                                ((ry - gridScreenPos.y -
                                                    grabOffset.y) / stepY)
                                                    .roundToInt()
                                                    .coerceAtLeast(0),
                                            )
                                            movePreview = placeWithPushDown(
                                                items, dragId, target, span,
                                            )
                                        }
                                    },
                                    onDragEnd = { cancelled ->
                                        val preview = movePreview
                                        if (!cancelled && preview != null) {
                                            controller.placeWidgets(
                                                screenId, preview,
                                            )
                                        }
                                        movingId = null
                                        movePreview = null
                                        dragStartItems = null
                                        // The widget STAYS in edit mode so
                                        // resize handles are one drag away.
                                    },
                                    movingThis = entry.appWidgetId == movingId,
                                    onPreviewSpan = { previewSpan = it },
                                    onDraggingChange = { dragging = it },
                                    onResizeDone = { span ->
                                        // Stay in edit mode after a release:
                                        // only tap-outside / Back / page
                                        // switch exits it.
                                        dragging = false
                                        // Persist the new span plus the
                                        // pushed-down positions it caused.
                                        val pos = resizePreview ?: basePos
                                        previewSpan = null
                                        controller.placeWidgets(
                                            screenId, pos,
                                            spans = mapOf(
                                                entry.appWidgetId to span,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        shortcutCells.forEach { (shortcut, app) ->
                            key("sc_${shortcut.id}") {
                                // Keep the source cell composed while its
                                // icon is dragged (hidden, not removed) —
                                // disposing the node kills the gesture.
                                ShortcutCell(
                                    shortcut = shortcut,
                                    app = app,
                                    iconTheme = iconTheme,
                                    hidden = (drag.payload as? DragPayload
                                        .Shortcut)?.shortcut?.id == shortcut.id,
                                    blur = editBlur > 0.01f,
                                    labelColor = aviate.homeText,
                                    menuOpen = shortcutMenuId == shortcut.id,
                                    onMenuOpen = {
                                        shortcutMenuId = shortcut.id
                                    },
                                    onMenuDismiss = { shortcutMenuId = null },
                                    onRemove = {
                                        shortcutMenuId = null
                                        controller.removeShortcut(shortcut.id)
                                    },
                                    onLaunch = { onLaunch(app) },
                                )
                            }
                        }
                        if (movingId != null || pendingDrop != null) {
                            // Blue slot at the dragged widget's live position
                            // or an incoming icon's target cell (measured to
                            // its span in the measure block).
                            Box(
                                Modifier
                                    .border(
                                        1.5.dp,
                                        Color.White.copy(alpha = 0.5f),
                                        editShape,
                                    )
                                    .background(
                                        Color.White.copy(alpha = 0.10f),
                                        editShape,
                                    ),
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val gapPx = gridGap.roundToPx()
                    val cellWPx = cellW.roundToPx()
                    val cellHPx = cellHeight.roundToPx()
                    val n = orderedWidgets.size
                    val m = shortcutCells.size
                    val dragIdx = orderedWidgets.indexOfFirst {
                        it.appWidgetId == movingId
                    }
                    val placeables = measurables.mapIndexed { i, ms ->
                        // [0,n) widgets, [n,n+m) shortcuts (1x1), [n+m] slot.
                        val s = when {
                            i < n -> layoutSpans[i]
                            i < n + m -> GridSpan(1, 1)
                            movingId != null && dragIdx >= 0 ->
                                layoutSpans[dragIdx]
                            else -> GridSpan(1, 1)
                        }
                        ms.measure(
                            Constraints.fixed(
                                s.cols * cellWPx + (s.cols - 1) * gapPx,
                                s.rows * cellHPx + (s.rows - 1) * gapPx,
                            )
                        )
                    }
                    val posOf = { id: Int -> livePos[id] ?: GridPos(0, 0) }
                    val usedRows = maxOf(
                        orderedWidgets.maxOfOrNull { e ->
                            posOf(e.appWidgetId).row +
                                layoutSpans[orderedWidgets.indexOf(e)].rows
                        } ?: 0,
                        shortcutCells.maxOfOrNull { cell ->
                            posOf(shortcutGridId(cell.first.id)).row + 1
                        } ?: 0,
                    )
                    // One spare row while editing or hovering a drop so items
                    // can be moved or grown past the current bottom.
                    val totalRows = usedRows +
                        if (movingId != null || previewSpan != null ||
                            pendingDrop != null
                        ) {
                            1
                        } else {
                            0
                        }
                    val totalH = if (totalRows == 0) 0
                        else totalRows * cellHPx + (totalRows - 1) * gapPx
                    layout(constraints.maxWidth, totalH) {
                        placeables.forEachIndexed { i, p ->
                            if (i == n + m) {
                                // Placeholder: move target or drop cell.
                                val gp = dropTarget
                                    ?: posOf(movingId ?: -1)
                                p.place(
                                    gp.col * (cellWPx + gapPx),
                                    gp.row * (cellHPx + gapPx),
                                )
                            } else if (i == dragIdx && movingId != null) {
                                // The real widget follows the finger.
                                val fx = fingerRaw.x - gridScreenPos.x -
                                    grabOffset.x
                                val fy = fingerRaw.y - gridScreenPos.y -
                                    grabOffset.y
                                p.place(
                                    fx.roundToInt().coerceIn(
                                        0, constraints.maxWidth - p.width,
                                    ),
                                    fy.roundToInt().coerceAtLeast(0),
                                    zIndex = 2f,
                                )
                            } else {
                                val gp = if (i < n) {
                                    posOf(orderedWidgets[i].appWidgetId)
                                } else {
                                    posOf(
                                        shortcutGridId(
                                            shortcutCells[i - n].first.id
                                        )
                                    )
                                }
                                p.place(
                                    gp.col * (cellWPx + gapPx),
                                    gp.row * (cellHPx + gapPx),
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
    editing: Boolean,
    movingThis: Boolean,
    contentBlur: Boolean,
    dragging: Boolean,
    cellW: Dp,
    controller: WidgetController,
    textShadow: Shadow?,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: (downRawX: Float, downRawY: Float) -> Unit,
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

    Box(
        // While this widget is being moved it floats under the finger,
        // slightly transparent and dimmed (the resize look).
        Modifier
            .fillMaxSize()
            .then(if (movingThis) Modifier.graphicsLayer { alpha = 0.7f } else Modifier)
            // Neighbours dim while a widget is edited (OxygenOS look);
            // Modifier.blur does not propagate into AndroidView content.
            .then(
                if (contentBlur) {
                    Modifier
                        .blur(16.dp)
                        .graphicsLayer { alpha = 0.4f }
                } else {
                    Modifier
                }
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (entry.title.isNotEmpty()) {
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            v.editMode = editing
                            v.onLongPress = onMenuOpen
                            v.onDragStart = onDragStart
                            v.onDragMove = onDragMove
                            v.onDragEnd = onDragEnd
                        }
                        // Compose blur/alpha don't reach AndroidView content;
                        // dim the view itself while a sibling is edited.
                        view.alpha = if (contentBlur) 0.4f else 1f
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            // Report once per size; the widget then picks the
                            // matching responsive layout.
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
                // A bit darker while floating under the finger.
                if (movingThis) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(editShape)
                            .background(Color.Black.copy(alpha = 0.25f)),
                    )
                }
                // Edit affordance: rounded white frame + translucent tint
                // while a handle is being dragged.
                if (editing) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .border(
                                1.5.dp,
                                Color.White.copy(alpha = 0.6f),
                                editShape,
                            )
                            .then(
                                if (dragging) {
                                    Modifier
                                        .clip(editShape)
                                        .background(
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
                val menuContent =
                    if (LocalIsDark.current) Color.White else Color(0xFF1C1C1C)
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = if (LocalIsDark.current) {
                        Color(0xFF2F2F2F)
                    } else {
                        Color(0xFFF2F2F2)
                    },
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(min = 260.dp),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.widget_rename),
                                color = menuContent,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = menuContent,
                            )
                        },
                        onClick = onRename,
                    )
                    HorizontalDivider(
                        color = menuContent.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 56.dp),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.remove_widget),
                                color = menuContent,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.HighlightOff,
                                contentDescription = null,
                                tint = menuContent,
                            )
                        },
                        onClick = onRemove,
                    )
                }
            }
        }

        if (editing && (horizontalResizable || verticalResizable)) {
            // One corner arc resizing both axes at once (OxygenOS style).
            CornerResizeHandle(
                modifier = Modifier.align(Alignment.BottomEnd),
                onPreview = { acc ->
                    onPreviewSpan(
                        dragSpan(
                            acc,
                            cols = horizontalResizable,
                            rows = verticalResizable,
                        )
                    )
                },
                onDraggingChange = onDraggingChange,
                onDone = { acc ->
                    onResizeDone(
                        dragSpan(
                            acc,
                            cols = horizontalResizable,
                            rows = verticalResizable,
                        )
                    )
                },
            )
        }
    }
}

/**
 * 48dp touch target drawing a white 90° arc hugging the frame's bottom-right
 * corner (OxygenOS style): 4dp round-cap stroke at the edit corner radius.
 */
@Composable
private fun CornerResizeHandle(
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
        contentAlignment = Alignment.BottomEnd,
    ) {
        Canvas(Modifier.size(24.dp)) {
            // Arc around the bottom-right corner, on the frame's curve.
            val r = editCorner.toPx() + 2.dp.toPx()
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(
                    size.width - r * 2f,
                    size.height - r * 2f,
                ),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}


/** A computed page drop: the shared preview/commit payload (result map + key). */
private data class PendingDrop(
    val positions: Map<Int, GridPos>,
    val componentKey: String,
    val movingShortcutId: Int?,
)

/**
 * A pinned app icon on a widget page — 1x1 cell, tap launches, long-press +
 * move drags, long-press alone opens Remove / App info.
 */
@Composable
private fun ShortcutCell(
    shortcut: PageShortcut,
    app: AppEntry,
    iconTheme: IconTheme?,
    hidden: Boolean,
    blur: Boolean,
    labelColor: Color,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRemove: () -> Unit,
    onLaunch: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (blur) Modifier.blur(16.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            app = app,
            iconTheme = iconTheme,
            showLabel = true,
            iconSize = 48.dp,
            labelColor = labelColor,
            modifier = Modifier.graphicsLayer {
                alpha = if (hidden) 0f else 1f
            },
            dragPayload = { DragPayload.Shortcut(shortcut, app) },
            onLongPress = onMenuOpen,
            onLaunch = onLaunch,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = onMenuDismiss,
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF2F2F2F) else Color(0xFFF2F2F2),
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            val contentColor = if (isDark) Color.White else Color(0xFF1C1C1C)
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.remove),
                        color = contentColor,
                        fontSize = 15.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = null,
                        tint = contentColor,
                    )
                },
                onClick = onRemove,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.dock_app_info),
                        color = contentColor,
                        fontSize = 15.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = contentColor,
                    )
                },
                onClick = {
                    onMenuDismiss()
                    openAppDetails(context, app)
                },
            )
        }
    }
}
