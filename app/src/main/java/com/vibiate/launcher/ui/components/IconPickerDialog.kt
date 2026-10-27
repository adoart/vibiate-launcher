package com.vibiate.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.IconOverride
import com.vibiate.launcher.data.IconPack
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.ui.rowColor
import com.vibiate.launcher.ui.rowTextColor
import com.vibiate.launcher.ui.sectionBandColor
import com.vibiate.launcher.ui.theme.AviateBlue
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-app icon picker: the icon as it resolves now, a suggestions row (the
 * original plus each pack that covers this app), a pack chooser with a
 * searchable icon grid, and "Reset to default" while an override is set.
 * Every choice writes an [IconOverride] and closes the dialog.
 */
@Composable
fun IconPickerDialog(
    app: AppEntry,
    iconPackManager: IconPackManager,
    settings: SettingsRepository,
    currentTheme: IconTheme?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val revision by iconPackManager.revision.collectAsState()
    val overrides by settings.iconOverrides.collectAsState(initial = emptyMap())
    val hasOverride = app.componentKey in overrides

    val packs by produceState<List<IconPack>>(initialValue = emptyList(), revision) {
        value = withContext(Dispatchers.Default) {
            iconPackManager.installedPacks()
                .mapNotNull { iconPackManager.loadPack(it.packageName) }
        }
    }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val day = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val selectedPack = packs.firstOrNull { it.packageName == selectedPkg }
        ?: currentTheme?.primary
        ?: packs.firstOrNull()

    // "Suggestions": packs that actually cover this app -> (pack, icon name).
    val suggestions by produceState<List<Pair<IconPack, String>>>(
        initialValue = emptyList(), packs, app.componentKey,
    ) {
        value = withContext(Dispatchers.Default) {
            packs.mapNotNull { pack ->
                pack.themedName(app.component, day)?.let { pack to it }
            }
        }
    }

    // Filtered icon names for the grid, off-main (packs carry ~16k names).
    val names by produceState<List<String>>(
        initialValue = emptyList(), selectedPack?.packageName, query,
    ) {
        value = withContext(Dispatchers.Default) {
            val all = selectedPack?.iconNames ?: emptyList()
            val q = query.trim().lowercase().replace(' ', '_')
            if (q.isEmpty()) all else all.filter { it.contains(q) }
        }
    }

    val currentBitmap = remember(app, currentTheme, overrides) {
        currentTheme?.bitmapFor(app.component, app.icon) ?: app.icon.toImageBitmap()
    }

    fun pick(override: IconOverride?) {
        scope.launch { settings.setIconOverride(app.componentKey, override) }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(sectionBandColor()),
        ) {
            AviateTopBar(stringResource(R.string.iconpicker_title), onDismiss)

            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // Header: icon as it resolves now + the app label.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowColor())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    currentBitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = app.label,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = app.label,
                        fontSize = 18.sp,
                        color = rowTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasOverride) {
                        TextButton(onClick = { pick(null) }) {
                            Text(stringResource(R.string.iconpicker_reset))
                        }
                    }
                }

                // Suggestions: the original icon, then one per covering pack.
                Text(
                    text = stringResource(R.string.iconpicker_suggestions),
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyRow {
                    item {
                        PickerCell(
                            bitmap = app.icon.toImageBitmap(96),
                            label = stringResource(R.string.iconpicker_original),
                        ) { pick(IconOverride.Original) }
                    }
                    items(suggestions, key = { it.first.packageName }) { (pack, name) ->
                        PickerCell(
                            bitmap = pack.loadDrawable(name)?.toImageBitmap(96),
                            label = pack.label,
                        ) { pick(IconOverride.Pack(pack.packageName, name)) }
                    }
                }

                // Pack chooser chips.
                LazyRow(Modifier.padding(vertical = 8.dp)) {
                    items(packs, key = { it.packageName }) { pack ->
                        val selected = pack.packageName == selectedPack?.packageName
                        Text(
                            text = pack.label,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else rowTextColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(
                                    if (selected) AviateBlue else rowColor(),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable { selectedPkg = pack.packageName }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.iconpicker_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f),
                ) {
                    items(names, key = { it }) { name ->
                        val pack = selectedPack ?: return@items
                        IconGridCell(pack, name) {
                            pick(IconOverride.Pack(pack.packageName, name))
                        }
                    }
                }
            }
        }
    }
}

/** 96px icon cell: loads its drawable asynchronously so 16k-name packs scroll. */
@Composable
private fun PickerCell(
    bitmap: ImageBitmap?,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(bitmap = it, contentDescription = label,
                    modifier = Modifier.size(48.dp))
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = rowTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IconGridCell(pack: IconPack, name: String, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, pack, name) {
        value = withContext(Dispatchers.IO) {
            pack.loadDrawable(name)?.toImageBitmap(96)
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(bitmap = it, contentDescription = name,
                    modifier = Modifier.size(48.dp))
            }
        }
        Text(
            text = name,
            fontSize = 9.sp,
            color = Color(0xFF999999),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
