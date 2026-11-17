package com.vibiate.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.BuildConfig
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconPackInfo
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.ThemeMode
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.data.WallpaperSchedule
import com.vibiate.launcher.data.WallpaperScheduleMode
import com.vibiate.launcher.data.WallpaperTarget
import com.vibiate.launcher.data.WALLPAPER_INTERVAL_PRESETS
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
    spaceEngine: com.vibiate.launcher.data.SpaceEngine,
    widgetController: com.vibiate.launcher.widget.WidgetController,
    wallpaperRotator: WallpaperRotator,
    initialSpaceId: Int? = null,
    onBack: () -> Unit = {},
    onRestoreHomeWidgets: () -> Unit = {},
    onAddWidgetToSpace: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Spaces sub-page: swaps the whole settings content (back pops it).
    var showSpaces by rememberSaveable {
        mutableStateOf(initialSpaceId != null)
    }
    if (showSpaces) {
        androidx.activity.compose.BackHandler { showSpaces = false }
        SpacesSettingsScreen(
            settings = settings,
            engine = spaceEngine,
            controller = widgetController,
            appRepository = appRepository,
            initialSpaceId = initialSpaceId,
            onBack = { showSpaces = false },
            onAddWidgetToSpace = onAddWidgetToSpace,
        )
        return
    }

    // Wallpaper images manager sub-page, same pattern as Spaces.
    var showImages by rememberSaveable { mutableStateOf(false) }
    if (showImages) {
        androidx.activity.compose.BackHandler { showImages = false }
        WallpaperImagesScreen(
            settings = settings,
            wallpaperRotator = wallpaperRotator,
            onBack = { showImages = false },
        )
        return
    }

    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
    val allowRotation by settings.allowRotation.collectAsState(initial = false)
    val iconPackPkg by settings.iconPackPackage.collectAsState(initial = "")
    val iconPackFallbacks by settings.iconPackFallbacks.collectAsState(
        initial = emptyList()
    )
    val wpEnabled by settings.wallpaperEnabled.collectAsState(initial = false)
    val wpImages by settings.wallpaperImages.collectAsState(initial = emptyList())
    val wpSchedule by settings.wallpaperSchedule.collectAsState(
        initial = WallpaperSchedule()
    )
    val wpTarget by settings.wallpaperTarget.collectAsState(
        initial = WallpaperTarget.BOTH
    )
    val wpShuffle by settings.wallpaperShuffle.collectAsState(initial = false)

    var showIconPackDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var pickStartTime by remember { mutableStateOf(false) }
    var pickExtraTime by remember { mutableStateOf(false) }
    val iconPacks by produceState<List<IconPackInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) { iconPackManager.installedPacks() }
    }

    // Exact-alarm capability (API 31+); re-read + re-arm on resume because the
    // user can grant it in system settings.
    val alarmManager = remember {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    fun canExactAlarms() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()
    var exactAlarms by remember { mutableStateOf(canExactAlarms()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = canExactAlarms()
                if (now != exactAlarms) {
                    exactAlarms = now
                    scope.launch { wallpaperRotator.reschedule() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun fmtTime(m: Int) = String.format("%02d:%02d", m / 60, m % 60)

    fun updateSchedule(ns: WallpaperSchedule) {
        scope.launch {
            settings.setWallpaperSchedule(ns)
            wallpaperRotator.reschedule()
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
            item(key = "allow_rotation") {
                AviateRow(
                    Icons.Filled.ScreenRotation,
                    stringResource(R.string.allow_rotation),
                ) {
                    Switch(
                        checked = allowRotation,
                        onCheckedChange = {
                            scope.launch { settings.setAllowRotation(it) }
                        },
                    )
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

            item { SectionBand(stringResource(R.string.wp_section)) }
            item {
                AviateRow(
                    Icons.Filled.Wallpaper,
                    stringResource(R.string.wp_enabled),
                ) {
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
                    Icons.Filled.PhotoLibrary,
                    stringResource(R.string.wp_images),
                    value = if (wpImages.isEmpty()) {
                        stringResource(R.string.wp_images_none)
                    } else {
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.wp_images_count,
                            wpImages.size,
                            wpImages.size,
                        )
                    },
                    onClick = { showImages = true },
                )
            }
            item {
                AviateRow(
                    Icons.Filled.Schedule,
                    stringResource(R.string.wp_schedule),
                    value = wallpaperScheduleSummary(wpSchedule),
                    onClick = { showScheduleDialog = true },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    AviateRow(
                        Icons.Filled.Alarm,
                        stringResource(R.string.wp_exact_timing),
                        value = stringResource(
                            if (exactAlarms) R.string.wp_exact_on
                            else R.string.wp_exact_off
                        ),
                        onClick = {
                            if (!exactAlarms) {
                                context.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse(
                                            "package:${context.packageName}"
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            item {
                AviateRow(
                    Icons.Filled.Wallpaper,
                    stringResource(R.string.wp_apply_to),
                    value = stringResource(
                        when (wpTarget) {
                            WallpaperTarget.HOME -> R.string.wp_target_home
                            WallpaperTarget.LOCK -> R.string.wp_target_lock
                            WallpaperTarget.BOTH -> R.string.wp_target_both
                        }
                    ),
                    onClick = { showTargetDialog = true },
                )
            }
            item {
                AviateRow(
                    Icons.Filled.Shuffle,
                    stringResource(R.string.wp_shuffle),
                ) {
                    Switch(
                        checked = wpShuffle,
                        onCheckedChange = {
                            scope.launch { settings.setWallpaperShuffle(it) }
                        },
                    )
                }
            }
            item {
                AviateRow(
                    Icons.Filled.Autorenew,
                    stringResource(R.string.wp_rotate_now),
                    onClick = { wallpaperRotator.rotateNow() },
                )
            }

            item { SectionBand("Spaces") }
            item {
                val resolution by spaceEngine.resolution.collectAsState()
                val spaces by settings.spaces.collectAsState(
                    initial = emptyList(),
                )
                val auto by settings.spacesAuto.collectAsState(initial = true)
                val active = spaces.firstOrNull {
                    it.id == resolution?.spaceId
                }?.name ?: ""
                AviateRow(
                    Icons.Filled.Widgets,
                    "Spaces",
                    value = (if (auto) "Automatic" else "Manual") +
                        if (active.isNotBlank()) " · $active" else "",
                    onClick = { showSpaces = true },
                )
            }

            item { SectionBand("Home") }
            item {
                AviateRow(
                    Icons.Filled.Widgets,
                    "Restore default Home widgets",
                    onClick = onRestoreHomeWidgets,
                )
            }
            item {
                AviateRow(
                    Icons.Filled.RestartAlt,
                    stringResource(R.string.dock_reset),
                    value = stringResource(R.string.dock_reset_subtitle),
                    onClick = { dockRepository.reset() },
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

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text(stringResource(R.string.wp_schedule)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    Row {
                        FilterChip(
                            selected = wpSchedule.mode ==
                                WallpaperScheduleMode.INTERVAL,
                            onClick = {
                                updateSchedule(
                                    wpSchedule.copy(
                                        mode = WallpaperScheduleMode.INTERVAL,
                                    ),
                                )
                            },
                            label = {
                                Text(stringResource(R.string.wp_mode_interval))
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = wpSchedule.mode ==
                                WallpaperScheduleMode.TIMES,
                            onClick = {
                                updateSchedule(
                                    wpSchedule.copy(
                                        mode = WallpaperScheduleMode.TIMES,
                                    ),
                                )
                            },
                            label = {
                                Text(stringResource(R.string.wp_mode_times))
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    when (wpSchedule.mode) {
                        WallpaperScheduleMode.INTERVAL -> {
                            WALLPAPER_INTERVAL_PRESETS.forEach { minutes ->
                                RadioOption(
                                    label = intervalLabel(minutes),
                                    selected =
                                        wpSchedule.intervalMinutes == minutes,
                                ) {
                                    val today =
                                        java.time.LocalDate.now().toEpochDay()
                                    updateSchedule(
                                        wpSchedule.copy(
                                            intervalMinutes = minutes,
                                            anchorEpochDay =
                                                if (minutes >= 1440) today
                                                else wpSchedule.anchorEpochDay,
                                        ),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { pickStartTime = true }) {
                                Text(
                                    stringResource(
                                        R.string.wp_starting_at,
                                        fmtTime(wpSchedule.startMinute),
                                    ),
                                )
                            }
                        }
                        WallpaperScheduleMode.TIMES -> {
                            wpSchedule.times.forEach { t ->
                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        fmtTime(t),
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        updateSchedule(
                                            wpSchedule.copy(
                                                times = wpSchedule.times - t,
                                            ),
                                        )
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription =
                                                stringResource(R.string.cancel),
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { pickExtraTime = true }) {
                                Text(stringResource(R.string.wp_add_time))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text(stringResource(R.string.spaces_close))
                }
            },
        )
    }

    if (pickStartTime) {
        TimePickDialog(
            initial = wpSchedule.startMinute,
            onPick = { m ->
                updateSchedule(wpSchedule.copy(startMinute = m))
                pickStartTime = false
            },
            onDismiss = { pickStartTime = false },
        )
    }

    if (pickExtraTime) {
        TimePickDialog(
            initial = 7 * 60,
            onPick = { m ->
                updateSchedule(
                    wpSchedule.copy(
                        times = (wpSchedule.times + m).distinct().sorted(),
                    ),
                )
                pickExtraTime = false
            },
            onDismiss = { pickExtraTime = false },
        )
    }

    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text(stringResource(R.string.wp_apply_to)) },
            text = {
                Column {
                    WallpaperTarget.entries.forEach { t ->
                        RadioOption(
                            label = stringResource(
                                when (t) {
                                    WallpaperTarget.HOME ->
                                        R.string.wp_target_home
                                    WallpaperTarget.LOCK ->
                                        R.string.wp_target_lock
                                    WallpaperTarget.BOTH ->
                                        R.string.wp_target_both
                                }
                            ),
                            selected = wpTarget == t,
                        ) {
                            scope.launch {
                                settings.setWallpaperTarget(t)
                            }
                            showTargetDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetDialog = false }) {
                    Text(stringResource(R.string.spaces_close))
                }
            },
        )
    }
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> androidx.compose.ui.res.pluralStringResource(
        R.plurals.wp_interval_minutes, minutes, minutes,
    )
    minutes < 1440 -> androidx.compose.ui.res.pluralStringResource(
        R.plurals.wp_interval_hours, minutes / 60, minutes / 60,
    )
    else -> androidx.compose.ui.res.pluralStringResource(
        R.plurals.wp_interval_days, minutes / 1440, minutes / 1440,
    )
}

@Composable
private fun wallpaperScheduleSummary(s: WallpaperSchedule): String {
    fun fmt(m: Int) = String.format("%02d:%02d", m / 60, m % 60)
    return when (s.mode) {
        WallpaperScheduleMode.INTERVAL ->
            if (s.intervalMinutes < 1440) {
                stringResource(
                    R.string.wp_every_interval,
                    intervalLabel(s.intervalMinutes),
                    fmt(s.startMinute),
                )
            } else {
                stringResource(
                    R.string.wp_every_days_at,
                    intervalLabel(s.intervalMinutes),
                    fmt(s.startMinute),
                )
            }
        WallpaperScheduleMode.TIMES -> stringResource(
            R.string.wp_daily_at,
            s.times.joinToString(", ") { fmt(it) },
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
    /** Small grey line under the title (hints that would crowd [value]). */
    subtitle: String? = null,
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
            Column(Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, color = rowTextColor())
                if (subtitle != null) {
                    Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF999999))
                }
            }
            if (value != null) {
                Text(text = value, fontSize = 13.sp, color = Color(0xFF999999))
            }
            trailing?.invoke()
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(dividerColor()))
    }
}
