package com.vibiate.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.IconPackTheme
import com.vibiate.launcher.ui.components.AlphabetRail
import com.vibiate.launcher.ui.components.AppIcon
import com.vibiate.launcher.ui.components.AviateSearchBar
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.theme.AviateBlue
import com.vibiate.launcher.ui.theme.LocalAviateColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

private data class LetterGroup(val letter: Char, val apps: List<AppEntry>)

/** Right-edge index rail reserves this much width. */
private val railWidth = 34.dp

/**
 * A–Z apps list, Aviate-style: flat white search bar, letter groups in a
 * 4-column grid where the letter itself occupies the first cell, and an
 * alphabetical index rail on the right edge.
 */
@Composable
fun AllAppsScreen(
    repository: AppRepository,
    iconPack: IconPackTheme?,
    onLaunch: (AppEntry) -> Unit,
    /** Fired once when an icon drag passes slop: move the pager to Collections. */
    onDragToCollections: () -> Unit = {},
    /** Emits to focus the search field (e.g. from the Collections bar). */
    focusRequests: Flow<Unit>? = null,
) {
    val apps by repository.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    if (focusRequests != null) {
        LaunchedEffect(focusRequests) {
            focusRequests.collect { focusRequester.requestFocus() }
        }
    }

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

    Column(Modifier.fillMaxSize()) {
        AviateSearchBar(
            hint = stringResource(R.string.search_apps),
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            trailing = {
                Text(
                    text = if (ascending) "A→Z" else "Z→A",
                    fontSize = 13.sp,
                    color = AviateBlue,
                    modifier = Modifier
                        .clickable { ascending = !ascending }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = railWidth),
            ) {
                itemsIndexed(groups, key = { _, g -> g.letter }) { index, group ->
                    LetterGroupRow(group, iconPack, onLaunch, onDragToCollections)
                    if (index < groups.lastIndex) {
                        GroupDivider()
                    }
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(railWidth),
            )
        }
    }
}

@Composable
private fun LetterGroupRow(
    group: LetterGroup,
    iconPack: IconPackTheme?,
    onLaunch: (AppEntry) -> Unit,
    onDragToCollections: () -> Unit,
) {
    val labelColor = LocalAviateColors.current.cardLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        // Rows of 4 equal-weight cells — exact fractions, nothing can wrap.
        // The letter fills cell 0 of the first row; apps fill the rest.
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = group.letter.toString(),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Thin,
                    fontFamily = FontFamily.Default,
                    color = AviateBlue,
                )
            }
            group.apps.take(3).forEach { app ->
                GroupAppCell(app, iconPack, labelColor, onLaunch, onDragToCollections)
            }
            repeat(3 - minOf(3, group.apps.size)) { Spacer(Modifier.weight(1f)) }
        }
        group.apps.drop(3).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    GroupAppCell(app, iconPack, labelColor, onLaunch, onDragToCollections)
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.GroupAppCell(
    app: AppEntry,
    iconPack: IconPackTheme?,
    labelColor: androidx.compose.ui.graphics.Color,
    onLaunch: (AppEntry) -> Unit,
    onDragToCollections: () -> Unit,
) {
    AppIcon(
        app = app,
        iconPack = iconPack,
        showLabel = true,
        labelSize = 13.sp,
        labelColor = labelColor,
        modifier = Modifier.weight(1f),
        dragPayload = { DragPayload.App(app, sourceKey = null) },
        onDragMoved = onDragToCollections,
    ) { onLaunch(app) }
}

/** 1px separator between letter groups, inset like Aviate's. */
@Composable
private fun GroupDivider() {
    Box(
        Modifier
            .padding(top = 18.dp, start = 8.dp, end = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalAviateColors.current.divider),
    )
}
