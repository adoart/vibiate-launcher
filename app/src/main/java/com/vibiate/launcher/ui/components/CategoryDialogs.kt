package com.vibiate.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.CategoryGlyph
import com.vibiate.launcher.data.CollectionDef
import com.vibiate.launcher.data.IconTheme

/** One icon+title option row used in the category menus. */
@Composable
private fun CategoryMenuRow(
    label: String,
    icon: ImageVector?,
    selected: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        if (selected != null) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Create or rename a category: single-line name field (max 24 chars) plus a
 * glyph grid; the selected glyph gets a primary-tinted circle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    title: String,
    initialName: String = "",
    initialGlyph: CategoryGlyph = CategoryGlyph.FOLDER,
    confirmLabel: String,
    onConfirm: (name: String, glyph: CategoryGlyph) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var glyph by remember { mutableStateOf(initialGlyph) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.category_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 6,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CategoryGlyph.entries.forEach { g ->
                        val selected = g == glyph
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { glyph = g },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = g.icon,
                                contentDescription = g.name,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), glyph) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Move one app between categories: "Automatic" (removes the override) plus a
 * radio row per collection. [currentKey] is the effective key, or null when
 * the app is on auto-classification.
 */
@Composable
fun MoveToCategoryDialog(
    app: AppEntry,
    collections: List<CollectionDef>,
    currentKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "auto") {
                    CategoryMenuRow(
                        label = stringResource(R.string.automatic),
                        icon = null,
                        selected = currentKey == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(collections, key = { it.key }) { def ->
                    CategoryMenuRow(
                        label = def.title,
                        icon = def.icon,
                        selected = currentKey == def.key,
                        onClick = { onSelect(def.key) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Checklist of every installed app for a custom category: small icon + label +
 * checkbox, with a label-filter field on top. Confirm emits the final
 * component-key selection.
 */
@Composable
fun AddAppsDialog(
    category: CollectionDef,
    apps: List<AppEntry>,
    initiallySelected: Set<String>,
    iconTheme: IconTheme?,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_apps_to, category.title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filtered, key = { it.componentKey }) { app ->
                        val checked = app.componentKey in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) {
                                        selected - app.componentKey
                                    } else {
                                        selected + app.componentKey
                                    }
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            AppIcon(
                                app = app,
                                iconTheme = iconTheme,
                                showLabel = false,
                                iconSize = 32.dp,
                                onLongPress = {},
                            ) {
                                selected = if (checked) {
                                    selected - app.componentKey
                                } else {
                                    selected + app.componentKey
                                }
                            }
                            Text(
                                text = app.label,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
