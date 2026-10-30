package com.vibiate.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.CollectionDef
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.UserCategory
import com.vibiate.launcher.data.dockKey
import com.vibiate.launcher.data.buildCollections
import com.vibiate.launcher.ui.components.AddAppsDialog
import com.vibiate.launcher.ui.components.AviateSearchBar
import com.vibiate.launcher.ui.components.CategoryEditorDialog
import com.vibiate.launcher.ui.components.CategorySection
import com.vibiate.launcher.ui.components.IconPickerDialog
import com.vibiate.launcher.ui.components.MoveToCategoryDialog
import com.vibiate.launcher.ui.drag.CardId
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.LocalDragDrop
import com.vibiate.launcher.ui.drag.SlotId
import com.vibiate.launcher.ui.drag.trackBounds
import com.vibiate.launcher.ui.theme.LocalAviateColors
import kotlinx.coroutines.launch

/**
 * Collections page: user-created categories first, then the built-in Aviate
 * ones — each collapsing to a [gridSize]-icon row and expanding to a full grid.
 * A trailing "+ New category" card creates user categories.
 */
@Composable
fun CollectionsScreen(
    repository: AppRepository,
    settings: SettingsRepository,
    iconTheme: IconTheme?,
    iconPackManager: IconPackManager,
    gridSize: Int,
    isCurrentPage: Boolean = true,
    onLaunch: (AppEntry) -> Unit,
    /** Tap on the Aviate search bar: hop to the A–Z page and focus its field. */
    onSearchClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drag = LocalDragDrop.current
    val density = LocalDensity.current
    val apps by repository.apps.collectAsState()
    val loaded by repository.loaded.collectAsState()
    // Null until DataStore emits: rendering built-ins first and prepending custom
    // cards later would leave them above the viewport (LazyColumn anchors by key).
    val customCategoriesOrNull by settings.customCategories
        .collectAsState(initial = null)
    val overridesOrNull by settings.categoryOverrides.collectAsState(initial = null)
    val categoryOrderOrNull by settings.categoryOrder.collectAsState(initial = null)
    val appOrderOrNull by settings.appOrder.collectAsState(initial = null)
    val customCategories = customCategoriesOrNull.orEmpty()
    val overrides = overridesOrNull.orEmpty()
    val categoryOrder = categoryOrderOrNull.orEmpty()
    val appOrder = appOrderOrNull.orEmpty()
    val settingsLoaded = customCategoriesOrNull != null &&
        overridesOrNull != null &&
        categoryOrderOrNull != null &&
        appOrderOrNull != null

    // Expanded/collapsed per category key, surviving config change.
    val expandedState = rememberSaveable {
        mutableStateOf(mapOf<String, Boolean>())
    }
    val expandedMap by expandedState

    val collections = remember(apps, customCategories, overrides, categoryOrder, appOrder) {
        buildCollections(apps, customCategories, overrides, categoryOrder, appOrder)
    }

    // Dialog state.
    var showNewCategory by remember { mutableStateOf(false) }
    var headerMenuDef by remember { mutableStateOf<CollectionDef?>(null) }
    var addAppsDef by remember { mutableStateOf<CollectionDef?>(null) }
    var editCategory by remember { mutableStateOf<UserCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<UserCategory?>(null) }
    var appMenuApp by remember { mutableStateOf<AppEntry?>(null) }
    var moveApp by remember { mutableStateOf<AppEntry?>(null) }
    var iconPickApp by remember { mutableStateOf<AppEntry?>(null) }

    // ---- Drag preview ----
    // The list the LazyColumn renders plus the drop data derived from it.
    val preview by derivedStateOf {
        val p = drag.payload
        val pt = drag.pointer
        // A dock icon or page shortcut drops like the app it resolves to
        // (copy semantics — the icon stays where it came from).
        val app: AppEntry? = when (p) {
            is DragPayload.App -> p.app
            is DragPayload.Dock -> p.entry.component?.let { cn ->
                apps.firstOrNull { it.componentKey == dockKey(cn) }
            }
            is DragPayload.Shortcut -> p.app
            else -> null
        }
        when {
            app != null -> {
                val ck = app.componentKey
                // Exact slot under the finger wins; otherwise the card as a
                // whole gets the drop appended (or pinned to the last visible
                // collapsed slot). Not over the dock — it owns that drop.
                var hoverKey: String? = null
                var hoverIndex = -1
                val overDock = drag.rects["dock"]?.contains(pt) == true ||
                    drag.rects["dock_remove"]?.contains(pt) == true
                if (!overDock) {
                for ((id, rect) in drag.rects) {
                    if (id is SlotId && rect.contains(pt)) {
                        hoverKey = id.key
                        hoverIndex = id.index
                        break
                    }
                }
                if (hoverKey == null) {
                    for ((id, rect) in drag.rects) {
                        if (id is CardId && rect.contains(pt)) {
                            hoverKey = id.key
                            val count = collections
                                .firstOrNull { it.first.key == id.key }
                                ?.second?.count { it.componentKey != ck } ?: 0
                            hoverIndex = if (expandedMap[id.key] == true) {
                                count
                            } else {
                                minOf(count, gridSize - 1)
                            }
                            break
                        }
                    }
                }
                }
                var dropTarget: Pair<String, List<String>>? = null
                val rows = collections.map { (def, catApps) ->
                    val filtered = catApps
                        .filter { it.componentKey != ck }
                        .map { it as AppEntry? }
                    if (def.key == hoverKey && hoverIndex >= 0) {
                        val idx = hoverIndex.coerceIn(0, filtered.size)
                        val withPlaceholder = filtered.toMutableList()
                            .apply { add(idx, null) }
                        dropTarget =
                            def.key to withPlaceholder.map { it?.componentKey ?: ck }
                        CollectionRow(def, withPlaceholder)
                    } else {
                        CollectionRow(def, filtered)
                    }
                }
                DragPreview(rows, appDrop = dropTarget)
            }
            p is DragPayload.Category -> {
                val draggedKey = p.def.key
                // Insert position = number of real cards above the finger.
                // The dragged card itself stays rendered (compact + dimmed):
                // its header hosts the gesture coroutine, and removing the
                // node would cancel the drag before it could drop.
                val insert = collections.count { (def, _) ->
                    def.key != draggedKey &&
                        drag.rects[CardId(def.key)]
                            ?.let { it.center.y < pt.y } == true
                }
                val rows = mutableListOf<Any>()
                val order = mutableListOf<String>()
                var seen = 0
                var placed = false
                collections.forEach { (def, catApps) ->
                    if (seen == insert && !placed) {
                        rows += CategoryDropPlaceholder
                        order += draggedKey
                        placed = true
                    }
                    rows += CollectionRow(def, catApps.map { it as AppEntry? })
                    if (def.key != draggedKey) {
                        order += def.key
                        seen++
                    }
                }
                if (!placed) {
                    rows += CategoryDropPlaceholder
                    order += draggedKey
                }
                DragPreview(rows, categoryDropOrder = order)
            }
            else -> DragPreview(
                collections.map { (def, catApps) ->
                    CollectionRow(def, catApps.map { it })
                },
            )
        }
    }
    val isAppDrag = drag.payload is DragPayload.App ||
        drag.payload is DragPayload.Shortcut ||
        (drag.payload as? DragPayload.Dock)?.entry?.component?.let { cn ->
            apps.any { it.componentKey == dockKey(cn) }
        } == true
    val isCategoryDrag = drag.payload is DragPayload.Category
    val draggedCategoryKey = (drag.payload as? DragPayload.Category)?.def?.key

    // ---- Drop commit ----
    // Zone handler: only while Collections is the current page and the
    // pointer is over it — the dock/remove zones win when they're hit.
    val latestPreview by rememberUpdatedState(preview)
    val latestCurrent by rememberUpdatedState(isCurrentPage)
    val latestApps by rememberUpdatedState(apps)
    DisposableEffect(drag) {
        val handler: (DragPayload, Offset) -> Boolean = { payload, pt ->
            if (!latestCurrent || drag.rects["collections_list"]?.contains(pt) != true) {
                false
            } else {
                // A dock icon drops like the app it resolves to (copy
                // semantics — it stays in the dock).
                val app: AppEntry? = when (payload) {
                    is DragPayload.App -> payload.app
                    is DragPayload.Dock -> payload.entry.component?.let { cn ->
                        latestApps.firstOrNull {
                            it.componentKey == dockKey(cn)
                        }
                    }
                    is DragPayload.Shortcut -> payload.app
                    else -> null
                }
                when {
                    app != null -> {
                        val drop = latestPreview.appDrop
                        if (drop == null) false else {
                            val (key, order) = drop
                            scope.launch {
                                settings.applyAppDrop(
                                    componentKey = app.componentKey,
                                    autoKey = app.category.name,
                                    targetKey = key,
                                    targetOrder = order,
                                )
                            }
                            true
                        }
                    }
                    payload is DragPayload.Category -> {
                        val order = latestPreview.categoryDropOrder
                        if (order == null) false else {
                            scope.launch { settings.setCategoryOrder(order) }
                            true
                        }
                    }
                    else -> false
                }
            }
        }
        drag.dropHandlers["collections"] = handler
        onDispose { if (drag.dropHandlers["collections"] === handler) {
            drag.dropHandlers.remove("collections")
        } }
    }

    // ---- Edge auto-scroll while dragging ----
    val listState = rememberLazyListState()
    LaunchedEffect(drag.isDragging) {
        val edge = with(density) { 64.dp.toPx() }
        while (drag.isDragging) {
            val rect = drag.rects["collections_list"]
            if (rect != null && drag.pointer.x in rect.left..rect.right) {
                when {
                    drag.pointer.y < rect.top + edge -> listState.scrollBy(-18f)
                    drag.pointer.y > rect.bottom - edge -> listState.scrollBy(18f)
                }
            }
            withFrameNanos {}
        }
    }

    val aviate = LocalAviateColors.current

    Column(Modifier.fillMaxSize()) {
        AviateSearchBar(
            hint = stringResource(R.string.search_apps),
            onClick = onSearchClick,
        )
        if (!settingsLoaded) {
            Box(Modifier.fillMaxSize())
        } else if (collections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (loaded) "No apps found" else "Loading apps…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .trackBounds("collections_list"),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                preview.rows,
                key = { item ->
                    (item as? CollectionRow)?.def?.key ?: "__placeholder__"
                },
            ) { item ->
                when (item) {
                    is CollectionRow -> {
                        val def = item.def
                        val expanded = expandedMap[def.key] ?: false
                        CategorySection(
                            def = def,
                            items = item.items,
                            expanded = expanded,
                            iconTheme = iconTheme,
                            gridSize = gridSize,
                            onToggle = {
                                val expanding = !expanded
                                expandedState.value =
                                    expandedMap + (def.key to expanding)
                                if (expanding) {
                                    // Chase the grow animation so the whole
                                    // card ends up inside the viewport.
                                    scope.launch {
                                        repeat(30) {
                                            withFrameNanos {}
                                            val info = listState.layoutInfo
                                            val item = info.visibleItemsInfo
                                                .firstOrNull { it.key == def.key }
                                                ?: return@launch
                                            val overflow = item.offset + item.size -
                                                info.viewportEndOffset
                                            if (overflow <= 0) return@launch
                                            listState.scrollBy(overflow.toFloat())
                                        }
                                    }
                                }
                            },
                            onLaunch = onLaunch,
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    if (def.key == draggedCategoryKey) {
                                        // Stays composed (its header hosts the
                                        // gesture coroutine) but takes no
                                        // visible space; the placeholder marks
                                        // the insertion point alone.
                                        Modifier.height(0.dp).alpha(0f)
                                    } else {
                                        Modifier
                                    },
                                ),
                            compact = isCategoryDrag,
                            forceSlots = isAppDrag,
                            onHeaderLongPress = if (def.isCustom) {
                                { headerMenuDef = def }
                            } else {
                                null
                            },
                            onAppLongPress = { appMenuApp = it },
                        )
                    }
                    CategoryDropPlaceholder -> Box(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(40.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            if (!isCategoryDrag) {
                item(key = "new_category") {
                    NewCategoryRow { showNewCategory = true }
                }
            }
        }
        }
    }

    // ---- Dialogs ----

    if (showNewCategory) {
        CategoryEditorDialog(
            title = stringResource(R.string.new_category),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name, glyph ->
                showNewCategory = false
                scope.launch { settings.addCustomCategory(name, glyph) }
            },
            onDismiss = { showNewCategory = false },
        )
    }

    // Custom category header long-press: Add apps / Edit / Delete.
    headerMenuDef?.let { def ->
        AlertDialog(
            onDismissRequest = { headerMenuDef = null },
            title = { Text(def.title) },
            text = {
                Column {
                    DialogTextRow(stringResource(R.string.add_apps)) {
                        headerMenuDef = null
                        addAppsDef = def
                    }
                    DialogTextRow(stringResource(R.string.edit)) {
                        headerMenuDef = null
                        editCategory = customCategories.firstOrNull { it.key == def.key }
                    }
                    DialogTextRow(stringResource(R.string.delete)) {
                        headerMenuDef = null
                        deleteCategory = customCategories.firstOrNull { it.key == def.key }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { headerMenuDef = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    addAppsDef?.let { def ->
        val initiallySelected = remember(def, overrides) {
            overrides.filterValues { it == def.key }.keys
        }
        AddAppsDialog(
            category = def,
            apps = apps,
            initiallySelected = initiallySelected,
            iconTheme = iconTheme,
            onConfirm = { selected ->
                addAppsDef = null
                val inCategory = overrides
                    .filterValues { it == def.key }.keys
                val toAdd = selected - inCategory
                val toRemove = inCategory - selected
                scope.launch {
                    if (toAdd.isNotEmpty()) settings.setOverrides(toAdd, def.key)
                    if (toRemove.isNotEmpty()) settings.setOverrides(toRemove, null)
                }
            },
            onDismiss = { addAppsDef = null },
        )
    }

    editCategory?.let { cat ->
        CategoryEditorDialog(
            title = stringResource(R.string.edit_category),
            initialName = cat.name,
            initialGlyph = cat.glyph,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name, glyph ->
                editCategory = null
                scope.launch {
                    settings.updateCustomCategory(cat.copy(name = name, glyph = glyph))
                }
            },
            onDismiss = { editCategory = null },
        )
    }

    deleteCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleteCategory = null },
            title = { Text(stringResource(R.string.delete_category_title, cat.name)) },
            text = { Text(stringResource(R.string.delete_category_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCategory = null
                        scope.launch { settings.deleteCustomCategory(cat.id) }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCategory = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // App icon long-press: move to category / app info.
    appMenuApp?.let { app ->
        AlertDialog(
            onDismissRequest = { appMenuApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    DialogTextRow(stringResource(R.string.move_to_category)) {
                        appMenuApp = null
                        moveApp = app
                    }
                    DialogTextRow(stringResource(R.string.change_icon)) {
                        appMenuApp = null
                        iconPickApp = app
                    }
                    DialogTextRow(stringResource(R.string.app_info)) {
                        appMenuApp = null
                        openAppDetails(context, app)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { appMenuApp = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    moveApp?.let { app ->
        val defs = collections.map { it.first }
        // Normalize stale overrides so "Automatic" is the preselection.
        val currentKey = overrides[app.componentKey]
            ?.takeIf { key -> defs.any { it.key == key } }
        MoveToCategoryDialog(
            app = app,
            collections = defs,
            currentKey = currentKey,
            onSelect = { key ->
                moveApp = null
                scope.launch { settings.setOverride(app.componentKey, key) }
            },
            onDismiss = { moveApp = null },
        )
    }

    iconPickApp?.let { app ->
        IconPickerDialog(
            app = app,
            iconPackManager = iconPackManager,
            settings = settings,
            currentTheme = iconTheme,
            onDismiss = { iconPickApp = null },
        )
    }
}

/** LazyColumn row: one collection card; [items] may hold null drop slots. */
private data class CollectionRow(val def: CollectionDef, val items: List<AppEntry?>)

/** Marker row rendered where a dragged category would land. */
private data object CategoryDropPlaceholder

/** Previewed rows plus the payload each drop type produces. */
private class DragPreview(
    val rows: List<Any>,
    val appDrop: Pair<String, List<String>>? = null,
    val categoryDropOrder: List<String>? = null,
)

/** The trailing "+ New category" action: a bare text button under the cards. */
@Composable
private fun NewCategoryRow(onClick: () -> Unit) {
    val aviate = LocalAviateColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = aviate.cardTitle,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.new_category),
            color = aviate.cardTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Plain text row used in the long-press menu dialogs. */
@Composable
private fun DialogTextRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

private fun openAppDetails(context: Context, app: AppEntry) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
