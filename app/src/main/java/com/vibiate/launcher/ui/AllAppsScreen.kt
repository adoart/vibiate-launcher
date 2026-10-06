package com.vibiate.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.IconPackTheme
import com.vibiate.launcher.ui.components.AlphabetRail
import com.vibiate.launcher.ui.components.AppIcon
import com.vibiate.launcher.ui.drag.DragPayload
import kotlinx.coroutines.launch

private data class LetterGroup(val letter: Char, val apps: List<AppEntry>)

/**
 * A–Z apps list: large outlined letter per group, 3 apps per row, alphabetical
 * index rail on the right edge, plus search and A-Z/Z-A sort.
 */
@Composable
fun AllAppsScreen(
    repository: AppRepository,
    iconPack: IconPackTheme?,
    onLaunch: (AppEntry) -> Unit,
    /** Fired once when an icon drag passes slop: move the pager to Collections. */
    onDragToCollections: () -> Unit = {},
) {
    val apps by repository.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var ascending by rememberSaveable { mutableStateOf(true) }

    val filtered = remember(apps, query, ascending) {
        val base = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        base.sortedWith(
            if (ascending) compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            else compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    val groups = remember(filtered) {
        filtered.groupBy { entry ->
            entry.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }.toSortedMap().map { (letter, list) -> LetterGroup(letter, list) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary

    // Mostly-opaque backdrop keeps the dense list readable over the wallpaper.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
    ) {
        // Header: A–Z APPS title + sort toggle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SortByAlpha,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "A–Z APPS",
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { ascending = !ascending }) {
                Text(
                    text = if (ascending) "A→Z" else "Z→A",
                    fontSize = 12.sp,
                    color = accent,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
        )

        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(groups, key = { _, g -> g.letter }) { _, group ->
                    LetterGroupRow(group, iconPack, accent, onLaunch, onDragToCollections)
                }
            }
            AlphabetRail(
                letters = groups.map { it.letter },
                onLetterSelected = { letter ->
                    // Rail emits any letter; snap to the nearest present group
                    // at or after it (rail order A–Z then #), else the last.
                    val railOrder = ('A'..'Z').toList() + '#'
                    val present = groups.map { it.letter }.toSet()
                    val target = railOrder
                        .drop(railOrder.indexOf(letter).coerceAtLeast(0))
                        .firstOrNull { it in present }
                        ?.let { groups.indexOfFirst { g -> g.letter == it } }
                        ?: groups.indices.last
                    if (groups.isNotEmpty()) scope.launch { listState.scrollToItem(target) }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterGroupRow(
    group: LetterGroup,
    iconPack: IconPackTheme?,
    accent: Color,
    onLaunch: (AppEntry) -> Unit,
    onDragToCollections: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Large thin cyan outline letter.
        Text(
            text = group.letter.toString(),
            modifier = Modifier.width(48.dp),
            style = TextStyle(
                color = accent.copy(alpha = 0.85f),
                fontSize = 56.sp,
                fontWeight = FontWeight.Thin,
                drawStyle = Stroke(width = 2.5f),
            ),
        )
        Spacer(Modifier.width(8.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.Start,
        ) {
            group.apps.forEach { app ->
                AppIcon(
                    app = app,
                    iconPack = iconPack,
                    showLabel = true,
                    modifier = Modifier.width(96.dp),
                    dragPayload = { DragPayload.App(app, sourceKey = null) },
                    onDragMoved = onDragToCollections,
                ) { onLaunch(app) }
            }
        }
    }
}
