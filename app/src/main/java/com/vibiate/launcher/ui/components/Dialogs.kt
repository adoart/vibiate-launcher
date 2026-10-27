package com.vibiate.launcher.ui.components

import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconPack
import com.vibiate.launcher.data.IconPackInfo
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.ui.SectionBand
import com.vibiate.launcher.ui.dividerColor
import com.vibiate.launcher.ui.rowColor
import com.vibiate.launcher.ui.rowTextColor
import com.vibiate.launcher.ui.sectionBandColor
import com.vibiate.launcher.ui.theme.AviateBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single radio row used by picker dialogs (grid size, font, icon pack, interval). */
@Composable
fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Full-screen icon-pack manager: primary pack (with per-pack preview strip and
 * coverage counts), ordered fallback packs, and the unthemed-icon mask switch.
 * Shared by the customize sheet and the settings screen.
 */
@Composable
fun IconPacksDialog(
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    appRepository: AppRepository,
    dockRepository: DockRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryPkg by settings.iconPackPackage.collectAsState(initial = "")
    val fallbackPkgs by settings.iconPackFallbacks.collectAsState(initial = emptyList())
    val maskUnthemed by settings.maskUnthemedIcons.collectAsState(initial = true)
    val revision by iconPackManager.revision.collectAsState()
    val packs by produceState<List<IconPackInfo>>(initialValue = emptyList(), revision) {
        value = withContext(Dispatchers.Default) { iconPackManager.installedPacks() }
    }

    // Preview strip apps: dock entries first, then alphabetical.
    val apps by appRepository.apps.collectAsState()
    val previewApps = remember(apps) {
        val dockKeys = dockRepository.entries.value
            .mapNotNull { e -> e.component?.let { "${it.packageName}/${it.className}" } }
            .toSet()
        val docked = apps.filter { it.componentKey in dockKeys }
        val rest = apps.filter { it.componentKey !in dockKeys }
            .sortedBy { it.label.lowercase() }
        (docked + rest).take(5)
    }

    val primaryPack by produceState<IconPack?>(
        initialValue = null, primaryPkg, revision,
    ) {
        value = withContext(Dispatchers.Default) {
            if (primaryPkg.isEmpty()) null else iconPackManager.loadPack(primaryPkg)
        }
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
            AviateTopBar(stringResource(R.string.iconpacks_title), onDismiss)
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionBand(stringResource(R.string.iconpacks_primary_section)) }
                item {
                    PackRow(
                        iconBitmap = null,
                        label = stringResource(R.string.icon_pack_default),
                        subtitle = stringResource(R.string.icon_pack_default_subtitle),
                        previews = emptyList(),
                        selected = primaryPkg.isEmpty(),
                    ) {
                        scope.launch { settings.setIconPack("") }
                    }
                }
                items(packs, key = { it.packageName }) { info ->
                    PackEntryRow(
                        info = info,
                        selected = primaryPkg == info.packageName,
                        iconPackManager = iconPackManager,
                        revision = revision,
                        apps = apps,
                        previewApps = previewApps,
                        context = context,
                        onSelect = {
                            scope.launch { settings.setIconPack(info.packageName) }
                        },
                    )
                }

                if (primaryPkg.isNotEmpty() && packs.size >= 2) {
                    item {
                        SectionBand(stringResource(R.string.iconpacks_fallbacks_section))
                    }
                    item {
                        Text(
                            text = stringResource(R.string.iconpacks_fallbacks_hint),
                            fontSize = 13.sp,
                            color = Color(0xFF999999),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowColor())
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                    val others = packs.filter { it.packageName != primaryPkg }
                    items(others, key = { "fb_${it.packageName}" }) { info ->
                        val index = fallbackPkgs.indexOf(info.packageName)
                        FallbackRow(
                            info = info,
                            position = if (index >= 0) index + 1 else null,
                        ) {
                            scope.launch {
                                settings.setIconPackFallbacks(
                                    if (index >= 0) {
                                        fallbackPkgs - info.packageName
                                    } else {
                                        fallbackPkgs + info.packageName
                                    }
                                )
                            }
                        }
                    }
                }

                item { SectionBand(stringResource(R.string.iconpacks_unthemed_section)) }
                item {
                    val hasMask = primaryPack?.hasMask == true
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(rowColor()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.iconpacks_mask),
                                    fontSize = 16.sp,
                                    color = rowTextColor(),
                                )
                                if (!hasMask) {
                                    Text(
                                        text = stringResource(R.string.iconpacks_no_mask),
                                        fontSize = 13.sp,
                                        color = Color(0xFF999999),
                                    )
                                }
                            }
                            Switch(
                                checked = maskUnthemed && hasMask,
                                enabled = hasMask,
                                onCheckedChange = { on ->
                                    scope.launch { settings.setMaskUnthemedIcons(on) }
                                },
                            )
                        }
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(dividerColor()),
                        )
                    }
                }
            }
        }
    }
}

/** Aviate-blue 56dp top bar with back arrow and an uppercase title. */
@Composable
internal fun AviateTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(AviateBlue),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One pack row: app icon, label, async "N icons · M of your apps" subtitle,
 * and a 5-icon preview strip rendered through the pack.
 */
@Composable
private fun PackEntryRow(
    info: IconPackInfo,
    selected: Boolean,
    iconPackManager: IconPackManager,
    revision: Int,
    apps: List<AppEntry>,
    previewApps: List<AppEntry>,
    context: Context,
    onSelect: () -> Unit,
) {
    // Triple(icon count, covered app count, preview bitmaps) — loaded and
    // rasterized off the main thread since appfilters reach ~50k items.
    // Previews go through a throwaway IconTheme so masked packs show the
    // composite look and the strip always has 5 icons.
    val loaded by produceState<Triple<Int, Int, List<ImageBitmap?>>?>(
        initialValue = null, info.packageName, revision,
    ) {
        value = withContext(Dispatchers.Default) {
            val pack = runCatching {
                iconPackManager.loadPack(info.packageName)
            }.getOrNull() ?: return@withContext null
            val day = java.util.Calendar.getInstance()
                .get(java.util.Calendar.DAY_OF_MONTH)
            val covered = apps.count { pack.themedName(it.component, day) != null }
            val theme = IconTheme(
                context = context.applicationContext,
                primary = pack,
                fallbacks = emptyList(),
                overrides = emptyMap(),
                maskUnthemed = true,
                manager = iconPackManager,
            )
            val previews = previewApps.map { app ->
                theme.bitmapFor(app.component, app.icon)
            }
            Triple(pack.filter.components.size, covered, previews)
        }
    }
    val packAppIcon by produceState<ImageBitmap?>(initialValue = null, info.packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(info.packageName)
            }.getOrNull()?.toImageBitmap()
        }
    }
    PackRow(
        iconBitmap = packAppIcon,
        label = info.label,
        subtitle = loaded?.let {
            stringResource(R.string.iconpacks_counts, it.first, it.second)
        },
        previews = loaded?.third.orEmpty().filterNotNull(),
        selected = selected,
        onClick = onSelect,
    )
}

/** Shared row shell for the system-default and pack entries. */
@Composable
private fun PackRow(
    iconBitmap: ImageBitmap?,
    label: String,
    subtitle: String?,
    previews: List<ImageBitmap>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(rowColor())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                iconBitmap?.let {
                    Image(bitmap = it, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = label, fontSize = 16.sp, color = rowTextColor())
                Text(
                    text = subtitle ?: stringResource(R.string.iconpacks_loading),
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
        if (previews.isNotEmpty()) {
            Row(Modifier.padding(start = 48.dp, bottom = 10.dp)) {
                previews.forEach { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 10.dp),
                    )
                }
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor()),
        )
    }
}

/** Checkbox row for a fallback pack; [position] shows its order when checked. */
@Composable
private fun FallbackRow(
    info: IconPackInfo,
    position: Int?,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(rowColor())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Checkbox(checked = position != null, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
            Text(
                text = info.label,
                fontSize = 16.sp,
                color = rowTextColor(),
                modifier = Modifier.weight(1f),
            )
            if (position != null) {
                Text(
                    text = position.toString(),
                    fontSize = 13.sp,
                    color = AviateBlue,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor()),
        )
    }
}
