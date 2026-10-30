package com.vibiate.launcher.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.DockEntry
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.dockKey
import com.vibiate.launcher.data.dockPlace
import com.vibiate.launcher.data.dockRemove
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.DockSlot
import com.vibiate.launcher.ui.drag.LocalDragDrop
import com.vibiate.launcher.ui.drag.launcherDragGestures
import com.vibiate.launcher.ui.drag.trackBounds
import com.vibiate.launcher.ui.theme.LocalIsDark
import kotlinx.coroutines.delay

private const val DRAG_THRESHOLD_PX = 40f

/**
 * Aviate-style dock: bare icons on the wallpaper over a soft bottom gradient,
 * a slim chevron handle, 5 icons always visible, and a second row of 5 that
 * expands via the handle or a quick vertical drag on the dock. Icons are
 * editable: long-press+move reorders (or lifts the icon onto the top Remove
 * zone), long-press opens a Remove/App info menu, and A–Z or category apps
 * dropped over the dock insert — or replace the hovered icon when full.
 */
@Composable
fun LauncherDock(
    repository: DockRepository,
    iconTheme: IconTheme?,
    /** Called when a page shortcut is dropped on the dock or the remove pill. */
    onShortcutDocked: (Int) -> Unit = {},
) {
    val entries by repository.entries.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var autoExpanded by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    val drag = LocalDragDrop.current
    LaunchedEffect(Unit) { repository.ensureLoaded() }

    // ---- Drop preview ----
    val hoveredSlot = remember { derivedStateOf { dockTargetIndex(drag.pointer, drag) } }
    val preview: DockPreview by remember {
        derivedStateOf {
            val real = entries
            val slots = real.map { e -> e?.component?.let(::dockKey) }
            when (val payload = drag.payload) {
                is DragPayload.Dock -> {
                    // The dragged icon's real cell stays composed (hidden):
                    // its node hosts the gesture coroutine — swapping it for
                    // another composable killed the stream before.
                    val key = payload.entry.component?.let(::dockKey)
                    val i = hoveredSlot.value
                    if (key == null || i < 0) {
                        DockPreview(
                            real.map { it },
                            hiddenIndex = payload.fromIndex,
                        )
                    } else {
                        val newSlots = dockPlace(
                            slots, key, i, payload.fromIndex,
                        )
                        DockPreview(
                            newSlots.map { k ->
                                k?.let { kk ->
                                    real.firstOrNull { e ->
                                        e?.component?.let(::dockKey) == kk
                                    }
                                }
                            },
                            ringIndex = i,
                            hiddenIndex = payload.fromIndex,
                        )
                    }
                }
                is DragPayload.App, is DragPayload.Shortcut -> {
                    // Preview = result: run the same dockPlace the commit
                    // uses and show where the app actually lands.
                    val i = hoveredSlot.value
                    if (i < 0) {
                        DockPreview(real.map { it })
                    } else {
                        val appKey = (payload as? DragPayload.App)
                            ?.app?.componentKey
                            ?: (payload as DragPayload.Shortcut)
                                .app.componentKey
                        val replace =
                            appKey !in slots && slots.all { it != null }
                        val newSlots = dockPlace(slots, appKey, i)
                        val cells = newSlots.map { k ->
                            k?.let { kk ->
                                real.firstOrNull { e ->
                                    e?.component?.let(::dockKey) == kk
                                }
                            }
                        }.toMutableList()
                        if (replace) cells[i] = real[i]
                        DockPreview(
                            cells,
                            ringIndex = i,
                            replaceIndex = if (replace) i else -1,
                        )
                    }
                }
                else -> DockPreview(real.map { it })
            }
        }
    }
    val latestHover by rememberUpdatedState(hoveredSlot.value)

    // Drop zones: "dock_remove" is the pill at the top of the screen.
    DisposableEffect(drag) {
        drag.dropHandlers["dock_remove"] = { p, pt ->
            if (drag.rects["dock_remove"]?.contains(pt) != true) {
                false
            } else when (p) {
                is DragPayload.Dock ->
                    p.entry.component?.let { cn ->
                        repository.edit { dockRemove(it, dockKey(cn)) }
                        true
                    } ?: false
                is DragPayload.Shortcut -> {
                    onShortcutDocked(p.shortcut.id)
                    true
                }
                else -> false
            }
        }
        drag.dropHandlers["dock"] = { p, pt ->
            when (p) {
                is DragPayload.App, is DragPayload.Shortcut ->
                    if (drag.rects["dock"]?.contains(pt) != true) {
                        false
                    } else {
                        dockTargetIndex(pt, drag).let { idx ->
                            if (idx < 0) false else {
                                val key = (p as? DragPayload.App)
                                    ?.app?.componentKey
                                    ?: (p as DragPayload.Shortcut)
                                        .app.componentKey
                                repository.edit { dockPlace(it, key, idx) }
                                (p as? DragPayload.Shortcut)?.let {
                                    onShortcutDocked(it.shortcut.id)
                                }
                                true
                            }
                        }
                    }
                is DragPayload.Dock ->
                    if (drag.rects["dock"]?.contains(pt) != true) {
                        false
                    } else {
                        p.entry.component?.let { cn ->
                            val idx = dockTargetIndex(pt, drag)
                            if (idx < 0 || idx == p.fromIndex) false else {
                                repository.edit {
                                    dockPlace(it, dockKey(cn), idx, p.fromIndex)
                                }
                                true
                            }
                        } ?: false
                    }
                else -> false
            }
        }
        onDispose {
            drag.dropHandlers.remove("dock_remove")
            drag.dropHandlers.remove("dock")
        }
    }

    // Hovering the collapsed dock with an app auto-expands it after 600ms.
    val appHovering = (drag.payload is DragPayload.App ||
        drag.payload is DragPayload.Shortcut) && latestHover >= 0
    LaunchedEffect(appHovering, expanded) {
        if (appHovering && !expanded) {
            delay(600)
            expanded = true
            autoExpanded = true
        }
    }
    LaunchedEffect(drag.isDragging) {
        if (!drag.isDragging && autoExpanded) {
            expanded = false
            autoExpanded = false
        }
    }

    // Bare icons on the wallpaper; a soft bottom gradient keeps them legible.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { total = 0f },
                        onDragCancel = { total = 0f },
                    ) { _, dragAmount ->
                        if (drag.isDragging) return@detectVerticalDragGestures
                        total += dragAmount
                        if (!expanded && total <= -DRAG_THRESHOLD_PX) {
                            expanded = true
                            total = 0f
                        } else if (expanded && total >= DRAG_THRESHOLD_PX) {
                            expanded = false
                            total = 0f
                        }
                    }
                }
                .trackBounds("dock")
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Slim centered chevron handle.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = stringResource(
                        if (expanded) R.string.dock_collapse else R.string.dock_expand
                    ),
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Second row (slots 5-9) expands upward.
            AnimatedVisibility(visible = expanded) {
                DockRow(
                    preview = preview,
                    realEntries = entries,
                    startIndex = 5,
                    iconTheme = iconTheme,
                    menuIndex = menuIndex,
                    onMenuOpen = { menuIndex = it },
                    onMenuDismiss = { menuIndex = null },
                    onRemove = { idx ->
                        entries.getOrNull(idx)?.component?.let { cn ->
                            repository.edit { dockRemove(it, dockKey(cn)) }
                        }
                    },
                )
            }
            DockRow(
                preview = preview,
                realEntries = entries,
                startIndex = 0,
                iconTheme = iconTheme,
                menuIndex = menuIndex,
                onMenuOpen = { menuIndex = it },
                onMenuDismiss = { menuIndex = null },
                onRemove = { idx ->
                    entries.getOrNull(idx)?.component?.let { cn ->
                        repository.edit { dockRemove(it, dockKey(cn)) }
                    }
                },
            )
        }
    }
}

/** Rendered rows: null slots show the drop ring placeholder. */
private data class DockPreview(
    val entries: List<DockEntry?>,
    // Dragged dock icon drawn hidden (its node must stay composed).
    val hiddenIndex: Int = -1,
    // Cell showing the landing ring overlay on top of its icon.
    val ringIndex: Int = -1,
    // Full-dock replace: the hovered icon dims behind a ring.
    val replaceIndex: Int = -1,
)

@Composable
private fun DockRow(
    preview: DockPreview,
    realEntries: List<DockEntry?>,
    startIndex: Int,
    iconTheme: IconTheme?,
    menuIndex: Int?,
    onMenuOpen: (Int) -> Unit,
    onMenuDismiss: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        repeat(5) { i ->
            val index = startIndex + i
            val shown = preview.entries.getOrNull(index)
            val isSource = index == preview.hiddenIndex
            val real = realEntries.getOrNull(index)
            // The DockIcon call must sit at the same slot position whether or
            // not this cell is the drag source — inside an if/else the two
            // calls are different nodes and toggling isSource disposes the
            // one hosting the gesture coroutine.
            val iconEntry = if (isSource) real else shown
            Box(
                Modifier
                    .weight(1f)
                    .trackBounds(DockSlot(index)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconEntry != null) {
                    DockIcon(
                        entry = iconEntry,
                        index = index,
                        iconTheme = iconTheme,
                        dimmed = index == preview.replaceIndex,
                        hidden = isSource,
                        menuOpen = menuIndex == index,
                        onMenuOpen = { onMenuOpen(index) },
                        onMenuDismiss = onMenuDismiss,
                        onRemove = { onRemove(index) },
                    )
                }
                // What the preview puts over the hidden source icon.
                if (isSource && shown != null && shown != real) {
                    DockIconVisual(entry = shown, iconTheme = iconTheme)
                }
                if (index == preview.ringIndex ||
                    index == preview.replaceIndex
                ) {
                    DockRing()
                }
            }
        }
    }
}

/** Ring marking the dock spot an icon would land on (circles a 48dp icon). */
@Composable
private fun DockRing() {
    Box(
        Modifier
            .size(52.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
    )
}

/**
 * 48dp touch target, no label. Tap launches; long-press + move drags;
 * long-press without moving opens the Remove/App info menu.
 */
@Composable
private fun DockIcon(
    entry: DockEntry,
    index: Int,
    iconTheme: IconTheme?,
    dimmed: Boolean,
    hidden: Boolean,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(56.dp)
            .launcherDragGestures(
                payload = {
                    entry.component?.let { DragPayload.Dock(entry, index) }
                },
                onTap = { runCatching { context.startActivity(entry.intent) } },
                onLongPressNoMove = { onMenuOpen() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val menuContent =
            if (LocalIsDark.current) Color.White else Color(0xFF1C1C1C)
        DockIconVisual(
            entry = entry,
            iconTheme = iconTheme,
            alpha = when {
                hidden -> 0f
                dimmed -> 0.4f
                else -> 1f
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = onMenuDismiss,
            shape = RoundedCornerShape(16.dp),
            containerColor = if (LocalIsDark.current) {
                Color(0xFF2F2F2F)
            } else {
                Color(0xFFF2F2F2)
            },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.dock_remove),
                        color = menuContent,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = null,
                        tint = menuContent,
                    )
                },
                onClick = {
                    onMenuDismiss()
                    onRemove()
                },
            )
            HorizontalDivider(
                color = menuContent.copy(alpha = 0.12f),
                modifier = Modifier.padding(start = 56.dp),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.dock_app_info),
                        color = menuContent,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = menuContent,
                    )
                },
                onClick = {
                    onMenuDismiss()
                    entry.component?.let { component ->
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${component.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                },
            )
        }
    }
}

/** The icon picture: themed bitmap at 48dp (same as AppIcon), glyph at 28dp. */
@Composable
private fun DockIconVisual(
    entry: DockEntry,
    iconTheme: IconTheme?,
    alpha: Float = 1f,
) {
    val bitmap = remember(entry, iconTheme) {
        entry.component?.let { iconTheme?.bitmapFor(it, entry.icon) }
            ?: entry.icon?.toImageBitmap()
    }
    val mod = Modifier.size(48.dp).graphicsLayer { this.alpha = alpha }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = entry.label,
            modifier = mod,
        )
    } else {
        Icon(
            imageVector = entry.glyph,
            contentDescription = entry.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = mod.size(28.dp),
        )
    }
}

/**
 * Dock slot under [pt]: only the cell containing it; over the dock but not
 * on a cell means no dock target (-1).
 */
private fun dockTargetIndex(pt: Offset, drag: com.vibiate.launcher.ui.drag.DragDropState): Int {
    if (drag.rects["dock"]?.contains(pt) != true) return -1
    return drag.rects.entries.firstNotNullOfOrNull { (id, rect) ->
        (id as? DockSlot)?.takeIf { rect.contains(pt) }?.index
    } ?: -1
}
