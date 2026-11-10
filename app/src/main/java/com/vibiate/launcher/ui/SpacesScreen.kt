package com.vibiate.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.PlaceTrigger
import com.vibiate.launcher.data.SpaceDef
import com.vibiate.launcher.data.SpaceReason
import com.vibiate.launcher.data.SpaceResolution
import com.vibiate.launcher.data.TimeWindow
import com.vibiate.launcher.data.spaceScreenId
import com.vibiate.launcher.ui.theme.LocalAviateColors
import com.vibiate.launcher.ui.theme.LocalIsDark
import com.vibiate.launcher.widget.WidgetController
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val chipTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** One space chip in the switcher (pill, glyph + name). */
@Composable
private fun SpaceChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    /** Auto-mode effective space: unfilled but accent-outlined. */
    marked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val aviate = LocalAviateColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(end = 8.dp)
            .background(
                color = if (selected) accent
                else aviate.cardBackground.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50),
            )
            .then(
                if (marked) {
                    Modifier.border(
                        1.5.dp, accent, RoundedCornerShape(50),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White
                else if (marked) accent else aviate.cardIcon,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) Color.White
            else if (marked) accent else aviate.homeText,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun dayMaskLabel(days: Int): String = when (days) {
    0x7F -> stringResource(R.string.spaces_days_every)
    0x1F -> stringResource(R.string.spaces_days_week)
    0x60 -> stringResource(R.string.spaces_days_weekend)
    else -> stringResource(R.string.spaces_day_letters).let { letters ->
        buildString {
            letters.forEachIndexed { i, n -> if (days and (1 shl i) != 0) append(n) }
        }
    }
}

/** Reason line under the space name (time window, headphones, place, …). */
@Composable
private fun reasonText(space: SpaceDef?, resolution: SpaceResolution?): String {
    if (resolution == null || space == null) return ""
    if (resolution.manual) return stringResource(R.string.space_reason_manual)
    if (SpaceReason.FALLBACK in resolution.reasons) {
        return stringResource(R.string.space_reason_fallback)
    }
    val parts = mutableListOf<String>()
    if (SpaceReason.TIME in resolution.reasons && space.time != null) {
        val t = space.time
        val start = LocalTime.of(t.startMinute / 60, t.startMinute % 60)
        val end = LocalTime.of(t.endMinute / 60, t.endMinute % 60)
        parts += "${dayMaskLabel(t.days)} · ${start.format(chipTimeFormat)}" +
            "–${end.format(chipTimeFormat)}"
    }
    if (SpaceReason.HEADPHONES in resolution.reasons) {
        parts += stringResource(R.string.space_reason_headphones)
    }
    if (SpaceReason.PLACE in resolution.reasons) {
        parts += when (space.place) {
            PlaceTrigger.HOME -> stringResource(R.string.space_reason_at_home)
            PlaceTrigger.WORK -> stringResource(R.string.space_reason_at_work)
            PlaceTrigger.AWAY -> stringResource(R.string.space_reason_away)
            else -> ""
        }
    }
    if (SpaceReason.CAR in resolution.reasons) {
        parts += stringResource(R.string.space_reason_driving)
    }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * The Spaces page (left of Home): a chip switcher on top of the active
 * space's widget/app grid (the same [WidgetsScreen] Home uses).
 */
@Composable
fun SpacesScreen(
    controller: WidgetController,
    spaces: List<SpaceDef>,
    resolution: SpaceResolution?,
    onSelectSpace: (Int?) -> Unit,
    onOpenSettings: (Int) -> Unit,
    apps: List<AppEntry>,
    iconTheme: IconTheme?,
    onLaunch: (AppEntry) -> Unit,
    isCurrentPage: Boolean,
    onDockRemove: (String) -> Unit,
    editBlur: Float,
    onEditingChange: (Boolean) -> Unit,
    exitEditRequest: Int,
) {
    val effective = resolution?.spaceId ?: spaces.firstOrNull()?.id ?: return
    val space = spaces.firstOrNull { it.id == effective }
    key(effective) {
        WidgetsScreen(
            controller = controller,
            screenId = spaceScreenId(effective),
            apps = apps,
            iconTheme = iconTheme,
            onLaunch = onLaunch,
            isCurrentPage = isCurrentPage,
            onDockRemove = onDockRemove,
            editBlur = editBlur,
            onEditingChange = onEditingChange,
            exitEditRequest = exitEditRequest,
            header = {
                val aviate = LocalAviateColors.current
                val textShadow: Shadow? = if (LocalIsDark.current) {
                    Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
                } else {
                    null
                }
                // Bring the effective space's chip into view (it can sit past
                // the right edge with four or more spaces).
                val chipScroll = rememberScrollState()
                val chipX = remember { mutableStateMapOf<Int, IntRange>() }
                LaunchedEffect(effective, chipX[effective]) {
                    val x = chipX[effective] ?: return@LaunchedEffect
                    val viewport = chipScroll.viewportSize
                    if (viewport <= 0) return@LaunchedEffect
                    val visible = chipScroll.value..(chipScroll.value + viewport)
                    if (x.first !in visible || x.last !in visible) {
                        chipScroll.animateScrollTo(
                            (x.last - viewport).coerceAtLeast(0),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScroll)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    SpaceChip(
                        label = stringResource(R.string.space_auto),
                        icon = null,
                        selected = resolution?.manual != true,
                    ) { onSelectSpace(null) }
                    spaces.forEach { s ->
                        SpaceChip(
                            label = s.name,
                            icon = s.glyph.icon,
                            selected = resolution?.manual == true &&
                                effective == s.id,
                            marked = resolution?.manual != true &&
                                effective == s.id,
                            modifier = Modifier.onGloballyPositioned { c ->
                                val x = c.positionInParent().x.toInt()
                                chipX[s.id] = x..(x + c.size.width)
                            },
                        ) { onSelectSpace(s.id) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = space?.name ?: "",
                            color = aviate.homeText,
                            fontSize = 26.sp,
                            style = LocalTextStyle.current.copy(
                                shadow = textShadow,
                            ),
                        )
                        val reason = reasonText(space, resolution)
                        if (reason.isNotBlank()) {
                            Text(
                                text = reason,
                                color = aviate.homeTextSecondary,
                                fontSize = 13.sp,
                                style = LocalTextStyle.current.copy(
                                    shadow = textShadow,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = { onOpenSettings(effective) }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(
                                R.string.space_edit_hint,
                            ),
                            tint = aviate.homeText,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            },
        )
    }
}
