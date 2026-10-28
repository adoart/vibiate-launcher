package com.vibiate.launcher.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.BuildConfig
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconPackInfo
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.ThemeMode
import com.vibiate.launcher.data.WallpaperInterval
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.ui.components.IconPacksDialog
import com.vibiate.launcher.ui.components.RadioOption
import com.vibiate.launcher.ui.theme.AviateBlue
import com.vibiate.launcher.ui.theme.LocalIsDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SectionBandLight = Color(0xFFECECEC)
private val SectionBandDark = Color(0xFF2D2E30)
private val RowBgLight = Color.White
private val RowBgDark = Color(0xFF252628)
private val RowIconLight = Color(0xFF333333)
private val RowIconDark = Color(0xFFBFBFBF)
private val RowDividerLight = Color(0xFFE5E5E5)
private val RowDividerDark = Color(0xFF333333)

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    appRepository: AppRepository,
    dockRepository: DockRepository,
    wallpaperRotator: WallpaperRotator,
    onBack: () -> Unit = {},
    onRestoreHomeWidgets: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
    val iconPackPkg by settings.iconPackPackage.collectAsState(initial = "")
    val iconPackFallbacks by settings.iconPackFallbacks.collectAsState(
        initial = emptyList()
    )
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

    Column(Modifier.fillMaxSize().background(sectionBandColor())) {
        // Aviate top app bar: solid cyan, back arrow + SETTINGS title.
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
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = "SETTINGS",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionBand("Appearance") }
            ThemeMode.entries.forEach { mode ->
                item(key = "theme_${mode.name}") {
                    val (icon, label) = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Filled.PhoneAndroid to "System"
                        ThemeMode.LIGHT -> Icons.Filled.WbSunny to "Light"
                        ThemeMode.DARK -> Icons.Filled.NightsStay to "Dark"
                    }
                    AviateRow(icon, label, onClick = {
                        scope.launch { settings.setThemeMode(mode) }
                    }) {
                        RadioButton(selected = themeMode == mode, onClick = null)
                    }
                }
            }

            item { SectionBand("Icon pack") }
            item {
                val primaryLabel = iconPacks
                    .firstOrNull { it.packageName == iconPackPkg }?.label
                val currentLabel = when {
                    primaryLabel == null -> "System default"
                    iconPackFallbacks.isEmpty() -> primaryLabel
                    else -> "$primaryLabel + ${iconPackFallbacks.size}"
                }
                AviateRow(Icons.Filled.Palette, "Icon pack", value = currentLabel,
                    onClick = { showIconPackDialog = true })
            }

            item { SectionBand("Wallpaper rotation") }
            item {
                AviateRow(Icons.Filled.Wallpaper, "Enabled") {
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
            }
            item {
                AviateRow(
                    Icons.Filled.Schedule,
                    "Interval",
                    value = WallpaperInterval.fromMinutes(wpInterval).label,
                    onClick = { showIntervalDialog = true },
                )
            }
            item {
                AviateRow(
                    Icons.Filled.PhotoLibrary,
                    "Images",
                    value = if (wpUris.isEmpty()) "None selected" else "${wpUris.size} selected",
                    onClick = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
            }
            item {
                AviateRow(Icons.Filled.Autorenew, "Rotate now",
                    onClick = { wallpaperRotator.rotateNow() })
            }

            item { SectionBand("Home") }
            item {
                AviateRow(
                    Icons.Filled.Widgets,
                    "Restore default Home widgets",
                    onClick = onRestoreHomeWidgets,
                )
            }

            item { SectionBand("Apps") }
            item {
                AviateRow(Icons.Filled.Refresh, "Rescan apps", value = "Reload installed app list",
                    onClick = { appRepository.invalidate() })
            }

            item { SectionBand("About") }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(rowColor())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "Vibiate — an Aviate-inspired launcher",
                        color = rowTextColor(),
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        color = Color(0xFF999999),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    if (showIconPackDialog) {
        IconPacksDialog(
            settings = settings,
            iconPackManager = iconPackManager,
            appRepository = appRepository,
            dockRepository = dockRepository,
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
internal fun sectionBandColor(): Color =
    if (LocalIsDark.current) SectionBandDark else SectionBandLight

@Composable
internal fun rowColor(): Color = if (LocalIsDark.current) RowBgDark else RowBgLight

@Composable
internal fun rowTextColor(): Color = if (LocalIsDark.current) Color.White else RowIconLight

@Composable
internal fun rowIconColor(): Color = if (LocalIsDark.current) RowIconDark else RowIconLight

@Composable
internal fun dividerColor(): Color = if (LocalIsDark.current) RowDividerDark else RowDividerLight

/** Full-width grey band naming the section below it. */
@Composable
internal fun SectionBand(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        color = Color(0xFF999999),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(sectionBandColor())
            .padding(start = 16.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

/** One Aviate settings row: leading icon, 16sp title, optional trailing slot. */
@Composable
internal fun AviateRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(rowColor())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 16.dp, end = 16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = rowIconColor(),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(32.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                color = rowTextColor(),
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(text = value, fontSize = 13.sp, color = Color(0xFF999999))
            }
            trailing?.invoke()
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(dividerColor()))
    }
}
