package com.vibiate.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Ordinal == pager page index. Custom screens and the add-page follow these. */
enum class LauncherTab { HOME, WIDGETS, COLLECTIONS, ALL_APPS }

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Current time that updates once per minute (just after each minute tick).
 * Reuse wherever minute-resolution time is needed, e.g. the home clock.
 */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // Sleep until just after the next minute tick.
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 250L)
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun rememberClock(): String = rememberNow().format(timeFormat)

/** Time-of-day glyph, like Aviate's contextual icon on the left of the top bar. */
private fun contextIcon(hour: Int): ImageVector = when (hour) {
    in 5..17 -> Icons.Filled.WbSunny
    in 18..21 -> Icons.Filled.WbTwilight
    else -> Icons.Filled.NightsStay
}

/**
 * Thin Aviate-style top bar: context icon left, clock center, space icons right
 * with the active one underlined. [activeTab] is null on custom/add pages, in
 * which case nothing is underlined.
 */
@Composable
fun LauncherTopBar(
    activeTab: LauncherTab?,
    onTabSelected: (LauncherTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = contextIcon(LocalDateTime.now().hour),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = rememberClock(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.End) {
            TabIcon(
                Icons.Filled.Home,
                stringResource(R.string.nav_home),
                activeTab == LauncherTab.HOME,
                accent,
            ) {
                onTabSelected(LauncherTab.HOME)
            }
            TabIcon(
                Icons.Filled.Widgets,
                stringResource(R.string.nav_widgets),
                activeTab == LauncherTab.WIDGETS,
                accent,
            ) {
                onTabSelected(LauncherTab.WIDGETS)
            }
            TabIcon(
                Icons.Filled.GridView,
                stringResource(R.string.nav_collections),
                activeTab == LauncherTab.COLLECTIONS,
                accent,
            ) {
                onTabSelected(LauncherTab.COLLECTIONS)
            }
            TabIcon(
                Icons.Filled.SortByAlpha,
                stringResource(R.string.nav_all_apps),
                activeTab == LauncherTab.ALL_APPS,
                accent,
            ) {
                onTabSelected(LauncherTab.ALL_APPS)
            }
            TabIcon(Icons.Filled.Settings, stringResource(R.string.nav_settings), false, accent, onSettingsClick)
        }
    }
}

@Composable
private fun TabIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            Modifier
                .width(24.dp)
                .height(2.dp)
                .background(if (active) accent else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}
