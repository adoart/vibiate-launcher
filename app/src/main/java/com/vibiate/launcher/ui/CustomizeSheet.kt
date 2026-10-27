package com.vibiate.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.LauncherFont
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.ui.components.IconPacksDialog
import com.vibiate.launcher.ui.components.RadioOption
import kotlinx.coroutines.launch

/**
 * Long-press "Customize" sheet: wallpaper (one-shot pick or rotation settings),
 * collection grid columns, icon pack, font family, and a shortcut into the full
 * launcher settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    appRepository: AppRepository,
    dockRepository: DockRepository,
    wallpaperRotator: WallpaperRotator,
    gridSize: Int,
    fontFamily: LauncherFont,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showIconPackDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    // Single-image pick -> decode -> WallpaperManager.setBitmap (one-shot).
    val pickWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch { wallpaperRotator.setOnce(uri) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.customize_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetRow(Icons.Filled.Widgets, stringResource(R.string.add_widget)) {
                onDismiss()
                onAddWidget()
            }
            SheetRow(Icons.Filled.Wallpaper, stringResource(R.string.customize_wallpaper)) {
                showWallpaperDialog = true
            }
            SheetRow(Icons.Filled.GridOn, stringResource(R.string.customize_grid)) {
                showGridDialog = true
            }
            SheetRow(Icons.Filled.Palette, stringResource(R.string.customize_icon_pack)) {
                showIconPackDialog = true
            }
            SheetRow(Icons.Filled.TextFields, stringResource(R.string.customize_font)) {
                showFontDialog = true
            }
            SheetRow(Icons.Filled.Settings, stringResource(R.string.customize_launcher_settings)) {
                onOpenSettings()
            }
        }
    }

    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text(stringResource(R.string.customize_wallpaper)) },
            text = {
                Column {
                    DialogOption(stringResource(R.string.wallpaper_single)) {
                        showWallpaperDialog = false
                        onDismiss()
                        pickWallpaper.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                    DialogOption(stringResource(R.string.wallpaper_rotating)) {
                        showWallpaperDialog = false
                        onOpenSettings()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showGridDialog) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            title = { Text(stringResource(R.string.customize_grid)) },
            text = {
                Column {
                    listOf(4, 5, 6).forEach { columns ->
                        RadioOption(
                            label = stringResource(R.string.grid_columns, columns),
                            selected = gridSize == columns,
                        ) {
                            scope.launch { settings.setGridSize(columns) }
                            showGridDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showIconPackDialog) {
        IconPacksDialog(
            settings = settings,
            iconPackManager = iconPackManager,
            appRepository = appRepository,
            dockRepository = dockRepository,
        ) { showIconPackDialog = false }
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(stringResource(R.string.customize_font)) },
            text = {
                Column {
                    LauncherFont.entries.forEach { font ->
                        RadioOption(
                            label = stringResource(font.labelRes),
                            selected = fontFamily == font,
                        ) {
                            scope.launch { settings.setFontFamily(font) }
                            showFontDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

/** Icon + label row inside the sheet. */
@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Plain tappable text row inside a chooser dialog (no radio). */
@Composable
private fun DialogOption(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
