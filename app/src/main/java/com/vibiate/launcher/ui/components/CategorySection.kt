package com.vibiate.launcher.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.CollectionDef
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.ui.drag.CardId
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.SlotId
import com.vibiate.launcher.ui.drag.launcherDragGestures
import com.vibiate.launcher.ui.drag.trackBounds
import com.vibiate.launcher.ui.theme.LocalAviateColors

/**
 * One Aviate-style collection card: a translucent 2dp-radius card holding the
 * glyph + letter-spaced title + chevron header, then [gridSize] fixed 56dp
 * slots per row — one label-free row when collapsed, all rows with labels when
 * expanded. Slot columns are SpaceBetween-spread so the first icon's image left
 * edge lines up with the header glyph's left edge (12dp row padding + AppIcon's
 * 4dp padding).
 *
 * [items] may contain `null` placeholders (the app slot a dragged icon would
 * land in); they render as an outlined circle. Every slot and the card itself
 * are registered as drop targets. [compact] renders only the header (used for
 * category drags); [forceSlots] renders one slot row on an empty custom card
 * instead of the hint so it can accept drops.
 */
@Composable
fun CategorySection(
    def: CollectionDef,
    items: List<AppEntry?>,
    expanded: Boolean,
    iconTheme: IconTheme?,
    gridSize: Int,
    onToggle: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    forceSlots: Boolean = false,
    onHeaderLongPress: (() -> Unit)? = null,
    onAppLongPress: ((AppEntry) -> Unit)? = null,
) {
    val aviate = LocalAviateColors.current
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableIntStateOf(0) }
    // Column pitch: labels may be this wide at most so they never overlap.
    val labelPitch = with(density) {
        (rowWidthPx.toDp() / gridSize).coerceAtLeast(56.dp)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .trackBounds(CardId(def.key)),
        shape = RoundedCornerShape(2.dp),
        color = aviate.cardBackground,
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .launcherDragGestures(
                        payload = { DragPayload.Category(def) },
                        onTap = onToggle,
                        onLongPressNoMove = onHeaderLongPress,
                    )
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = def.icon,
                    contentDescription = null,
                    tint = aviate.cardIcon,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = def.title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = aviate.cardTitle,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = aviate.cardIcon,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }

            if (!compact) {
                if (def.isCustom && items.isEmpty() && !forceSlots) {
                    Text(
                        text = stringResource(R.string.category_empty_hint),
                        fontSize = 13.sp,
                        color = aviate.cardIcon,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 10.dp,
                        ),
                    )
                } else {
                    // Fixed 56dp slots: identical columns collapsed or expanded.
                    // Slot index = row * gridSize + col; empty slots are drop
                    // targets too, so every slot tracks its bounds.
                    val rows = (
                        if (expanded) items.chunked(gridSize)
                        else listOf(items.take(gridSize))
                        ).ifEmpty { listOf(emptyList()) }
                    rows.forEachIndexed { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { rowWidthPx = it.width }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            repeat(gridSize) { col ->
                                val index = rowIndex * gridSize + col
                                val app = rowItems.getOrNull(col)
                                // The slot Box is always present: the drag
                                // gesture must survive its own preview, which
                                // swaps the icon out for the placeholder ring —
                                // disposing the gesture node would cancel it.
                                Box(
                                    Modifier
                                        .width(56.dp)
                                        .trackBounds(SlotId(def.key, index))
                                        .launcherDragGestures(
                                            payload = {
                                                app?.let { DragPayload.App(it, def.key) }
                                            },
                                            onTap = { app?.let(onLaunch) },
                                            onLongPressNoMove = app?.let { a ->
                                                onAppLongPress?.let { lp -> { lp(a) } }
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        app != null -> Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AppIcon(
                                                app = app,
                                                iconTheme = iconTheme,
                                                showLabel = false,
                                                interactive = false,
                                            ) {}
                                            if (expanded) {
                                                Text(
                                                    text = app.label,
                                                    fontSize = 12.sp,
                                                    color = aviate.cardLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    // Wider than the 56dp slot
                                                    // is fine: pitch <= column
                                                    // spacing, so neighbours
                                                    // never overlap.
                                                    modifier = Modifier
                                                        .requiredWidth(labelPitch),
                                                )
                                            }
                                        }
                                        col < rowItems.size -> Box(
                                            // Drop placeholder ring.
                                            Modifier
                                                .size(48.dp)
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
