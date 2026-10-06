package com.vibiate.launcher.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.BuildConfig
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.IconPackInfo
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.ThemeMode
import com.vibiate.launcher.data.WallpaperInterval
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.ui.components.IconPackPickerDialog
import com.vibiate.launcher.ui.components.RadioOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    appRepository: AppRepository,
    wallpaperRotator: WallpaperRotator,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
    val iconPackPkg by settings.iconPackPackage.collectAsState(initial = "")
    val wpEnabled by settings.wallpaperEnabled.collectAsState(initial = false)
    val wpInterval by settings.wallpaperIntervalMinutes.collectAsState(initial = WallpaperInterval.H24.minutes)
    val wpUris by settings.wallpaperUris.collectAsState(initial = emptyList())

    var showIconPackDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    val iconPacks by produceState<List<IconPackInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) { iconPackManager.installedPacks() }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            scope.launch {
                settings.setWallpaperUris(uris.map { it.toString() })
                wallpaperRotator.reschedule()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item { SectionHeader("Appearance") }
        item {
            SettingsCard {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { settings.setThemeMode(mode) } }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Icon pack") }
        item {
            val currentLabel = iconPacks.firstOrNull { it.packageName == iconPackPkg }?.label
                ?: "System default"
            SettingsCard {
                SettingsRow("Icon pack", currentLabel) { showIconPackDialog = true }
            }
        }

        item { SectionHeader("Wallpaper rotation") }
        item {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enabled", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = wpEnabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                settings.setWallpaperEnabled(on)
                                wallpaperRotator.reschedule()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    "Interval",
                    WallpaperInterval.fromMinutes(wpInterval).label,
                ) { showIntervalDialog = true }
                SettingsRow(
                    "Images",
                    if (wpUris.isEmpty()) "None selected" else "${wpUris.size} selected",
                ) {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                SettingsRow("Rotate now", "") { wallpaperRotator.rotateNow() }
            }
        }

        item { SectionHeader("Apps") }
        item {
            SettingsCard {
                SettingsRow("Rescan apps", "Reload installed app list") {
                    appRepository.invalidate()
                }
            }
        }

        item { SectionHeader("About") }
        item {
            SettingsCard {
                Text(
                    text = "Vibiate — an Aviate-inspired launcher",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showIconPackDialog) {
        IconPackPickerDialog(
            settings = settings,
            iconPackManager = iconPackManager,
        ) { showIconPackDialog = false }
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Rotation interval") },
            text = {
                Column {
                    WallpaperInterval.entries.forEach { interval ->
                        RadioOption(
                            label = interval.label,
                            selected = wpInterval == interval.minutes,
                        ) {
                            scope.launch {
                                settings.setWallpaperInterval(interval.minutes)
                                wallpaperRotator.reschedule()
                            }
                            showIntervalDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        if (value.isNotEmpty()) {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

