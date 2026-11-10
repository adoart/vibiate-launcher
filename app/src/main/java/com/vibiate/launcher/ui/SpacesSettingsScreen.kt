package com.vibiate.launcher.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.PlaceTrigger
import com.vibiate.launcher.data.LatLng
import com.vibiate.launcher.data.SavedPlace
import com.vibiate.launcher.data.SpaceDef
import com.vibiate.launcher.data.SpaceEngine
import com.vibiate.launcher.data.SpaceGlyph
import com.vibiate.launcher.data.TimeWindow
import com.vibiate.launcher.data.TriggerMatch
import com.vibiate.launcher.data.spaceScreenId
import com.vibiate.launcher.ui.components.RadioOption
import com.vibiate.launcher.ui.theme.AviateBlue
import com.vibiate.launcher.widget.WidgetController
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun hasPermission(context: Context, p: String): Boolean =
    ContextCompat.checkSelfPermission(context, p) ==
        PackageManager.PERMISSION_GRANTED

private fun hasLocationPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasBtPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)

private fun minuteToTime(m: Int): LocalTime = LocalTime.of(m / 60, m % 60)

@Composable
private fun placeSummary(place: SavedPlace?): String = place?.let {
    stringResource(R.string.spaces_place_format, it.lat, it.lng, it.radiusM)
} ?: stringResource(R.string.spaces_place_not_set)

@Composable
private fun SpaceDef.triggerSummary(): String = buildList {
    if (time != null) {
        add("${minuteToTime(time.startMinute).format(timeFmt)}" +
            "–${minuteToTime(time.endMinute).format(timeFmt)}")
    }
    if (headphones) add(stringResource(R.string.spaces_trig_headphones))
    if (place != PlaceTrigger.NONE) add(
        when (place) {
            PlaceTrigger.HOME -> stringResource(R.string.spaces_loc_home)
            PlaceTrigger.WORK -> stringResource(R.string.spaces_loc_work)
            else -> stringResource(R.string.spaces_trig_away)
        },
    )
    if (car) add(stringResource(R.string.spaces_driving))
}.joinToString(" · ").ifBlank { stringResource(R.string.spaces_trig_none) }

/**
 * The Spaces section of Settings: switching options, places, car devices
 * and the space list; tapping a space opens its editor.
 */
@Composable
fun SpacesSettingsScreen(
    settings: com.vibiate.launcher.data.SettingsRepository,
    engine: SpaceEngine,
    controller: WidgetController,
    appRepository: AppRepository,
    initialSpaceId: Int?,
    onBack: () -> Unit,
    onAddWidgetToSpace: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spaces by settings.spaces.collectAsState(initial = null)
    val auto by settings.spacesAuto.collectAsState(initial = true)
    val fallback by settings.spaceFallback.collectAsState(
        initial = com.vibiate.launcher.data.DEFAULT_SPACE_FALLBACK,
    )
    val placeHome by settings.placeHome.collectAsState(initial = null)
    val placeWork by settings.placeWork.collectAsState(initial = null)
    val carDevices by settings.carBtDevices.collectAsState(
        initial = emptyList(),
    )
    val resolution by engine.resolution.collectAsState()
    val apps by appRepository.apps.collectAsState()

    var editingId by remember { mutableStateOf(initialSpaceId) }
    var showFallbackDialog by remember { mutableStateOf(false) }
    var placeDialog by remember { mutableStateOf<String?>(null) } // "home"/"work"
    var showCarDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var renameSpace by remember { mutableStateOf<Int?>(null) }
    var iconPickSpace by remember { mutableStateOf<Int?>(null) }
    var timePick by remember { mutableStateOf<String?>(null) }
    var placeTriggerDialog by remember { mutableStateOf<Int?>(null) }
    var appPickSpace by remember { mutableStateOf<Int?>(null) }

    val list = spaces.orEmpty()
    val editing = list.firstOrNull { it.id == editingId }

    fun update(space: SpaceDef) {
        scope.launch {
            settings.setSpaces(list.map { if (it.id == space.id) space else it })
        }
    }

    fun move(from: Int, delta: Int) {
        val to = from + delta
        if (to !in list.indices) return
        scope.launch {
            val l = list.toMutableList()
            val it = l.removeAt(from)
            l.add(to, it)
            settings.setSpaces(l)
        }
    }

    // Permission launchers (location / Bluetooth) requested from dialogs.
    var pendingPlaceAfterGrant by remember { mutableStateOf(false) }
    var placeFill by remember { mutableStateOf<LatLng?>(null) }
    var pendingCarAfterGrant by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        engine.refreshPermissions()
        if (pendingPlaceAfterGrant && hasLocationPermission(context)) {
            pendingPlaceAfterGrant = false
            scope.launch {
                placeFill = engine.currentLocation()
            }
        }
        if (pendingCarAfterGrant) {
            pendingCarAfterGrant = false
            if (hasBtPermission(context)) showCarDialog = true
        }
    }

    Column(Modifier.fillMaxSize().background(sectionBandColor())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(AviateBlue),
        ) {
            IconButton(
                onClick = {
                    if (editing != null) editingId = null else onBack()
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = editing?.name?.uppercase() ?: stringResource(R.string.spaces_list_title).uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (editing == null) {
                item { SectionBand(stringResource(R.string.spaces_switching)) }
                item {
                    AviateRow(Icons.Filled.SwapVert, stringResource(R.string.spaces_auto_switch)) {
                        Switch(
                            checked = auto,
                            onCheckedChange = {
                                scope.launch { settings.setSpacesAuto(it) }
                            },
                        )
                    }
                }
                item {
                    val fb = list.firstOrNull { it.id == fallback }?.name
                        ?: list.firstOrNull()?.name ?: ""
                    AviateRow(
                        Icons.Filled.Star,
                        stringResource(R.string.spaces_fallback_row),
                        value = fb,
                        onClick = { showFallbackDialog = true },
                    )
                }

                item { SectionBand(stringResource(R.string.spaces_places)) }
                item {
                    AviateRow(
                        Icons.Filled.LocationOn,
                        stringResource(R.string.spaces_place_home),
                        value = placeSummary(placeHome),
                        onClick = { placeDialog = "home" },
                    )
                }
                item {
                    AviateRow(
                        Icons.Filled.Place,
                        stringResource(R.string.spaces_place_work),
                        value = placeSummary(placeWork),
                        onClick = { placeDialog = "work" },
                    )
                }

                item { SectionBand(stringResource(R.string.spaces_car)) }
                item {
                    val v = when {
                        !hasBtPermission(context) -> stringResource(R.string.spaces_car_perm)
                        else -> pluralStringResource(R.plurals.spaces_car_selected, carDevices.size, carDevices.size)
                    }
                    AviateRow(
                        Icons.Filled.Bluetooth,
                        stringResource(R.string.spaces_car_bt),
                        value = v,
                        onClick = {
                            if (hasBtPermission(context)) {
                                showCarDialog = true
                            } else {
                                pendingCarAfterGrant = true
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                    ),
                                )
                            }
                        },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.spaces_car_hint),
                        color = Color(0xFF999999),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    )
                }

                item {
                    SectionBand(stringResource(R.string.spaces_list_title))
                    Text(
                        stringResource(R.string.spaces_priority_hint),
                        color = Color(0xFF999999),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(sectionBandColor())
                            .padding(start = 16.dp, bottom = 8.dp),
                    )
                }
                lazyItems(list, key = { it.id }) { space ->
                    val i = list.indexOf(space)
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
                                .clickable { editingId = space.id }
                                .padding(start = 16.dp),
                        ) {
                            Icon(
                                imageVector = space.glyph.icon,
                                contentDescription = null,
                                tint = rowIconColor(),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    space.name,
                                    fontSize = 16.sp,
                                    color = rowTextColor(),
                                )
                                Text(
                                    space.triggerSummary(),
                                    fontSize = 12.sp,
                                    color = Color(0xFF999999),
                                )
                            }
                            IconButton(
                                onClick = { move(i, -1) },
                                enabled = i > 0,
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.spaces_move_up),
                                    tint = if (i > 0) rowIconColor()
                                    else rowIconColor().copy(alpha = 0.3f),
                                )
                            }
                            IconButton(
                                onClick = { move(i, 1) },
                                enabled = i < list.lastIndex,
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.spaces_move_down),
                                    tint = if (i < list.lastIndex) rowIconColor()
                                    else rowIconColor().copy(alpha = 0.3f),
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
                item {
                    AviateRow(
                        Icons.Filled.Add,
                        stringResource(R.string.add_space),
                        onClick = {
                            val nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
                            val def = SpaceDef(
                                id = nextId,
                                name = context.getString(R.string.space_name, nextId),
                                glyph = SpaceGlyph.STAR,
                            )
                            scope.launch {
                                settings.setSpaces(list + def)
                            }
                            editingId = nextId
                        },
                    )
                }
            } else {
                // ---- Space editor ----
                val space = editing
                item { SectionBand(stringResource(R.string.spaces_general)) }
                item {
                    AviateRow(
                        Icons.Filled.Edit,
                        stringResource(R.string.spaces_name),
                        value = space.name,
                        onClick = { renameSpace = space.id },
                    )
                }
                item {
                    AviateRow(
                        space.glyph.icon,
                        stringResource(R.string.spaces_icon),
                        onClick = { iconPickSpace = space.id },
                    )
                }

                item { SectionBand(stringResource(R.string.spaces_triggers)) }
                item {
                    AviateRow(
                        Icons.Filled.SwapVert,
                        stringResource(R.string.spaces_use_auto),
                    ) {
                        Switch(
                            checked = space.autoEnabled,
                            onCheckedChange = {
                                update(space.copy(autoEnabled = it))
                            },
                        )
                    }
                }
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(rowColor())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.spaces_match),
                            fontSize = 16.sp,
                            color = rowTextColor(),
                        )
                        Row {
                            FilterChip(
                                selected = space.match == TriggerMatch.ANY,
                                onClick = {
                                    update(space.copy(match = TriggerMatch.ANY))
                                },
                                label = { Text(stringResource(R.string.spaces_match_any)) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            FilterChip(
                                selected = space.match == TriggerMatch.ALL,
                                onClick = {
                                    update(space.copy(match = TriggerMatch.ALL))
                                },
                                label = { Text(stringResource(R.string.spaces_match_all)) },
                            )
                        }
                    }
                }
                item {
                    AviateRow(Icons.Filled.Schedule, stringResource(R.string.spaces_time)) {
                        Switch(
                            checked = space.time != null,
                            onCheckedChange = { on ->
                                update(
                                    space.copy(
                                        time = if (on) {
                                            TimeWindow(9 * 60, 18 * 60)
                                        } else {
                                            null
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                space.time?.let { tw ->
                    item {
                        AviateRow(
                            Icons.Filled.Schedule,
                            stringResource(R.string.spaces_starts),
                            value = minuteToTime(tw.startMinute)
                                .format(timeFmt),
                            onClick = { timePick = "start" },
                        )
                    }
                    item {
                        AviateRow(
                            Icons.Filled.Schedule,
                            stringResource(R.string.spaces_ends),
                            value = minuteToTime(tw.endMinute).format(timeFmt),
                            onClick = { timePick = "end" },
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(rowColor())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            listOf("M", "T", "W", "T", "F", "S", "S")
                                .forEachIndexed { i, d ->
                                    val bit = 1 shl i
                                    FilterChip(
                                        selected = tw.days and bit != 0,
                                        onClick = {
                                            update(
                                                space.copy(
                                                    time = tw.copy(
                                                        days = tw.days xor bit,
                                                    ),
                                                ),
                                            )
                                        },
                                        label = { Text(d) },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                        }
                    }
                }
                item {
                    AviateRow(
                        Icons.Filled.Headphones,
                        stringResource(R.string.spaces_headphones),
                        subtitle = stringResource(R.string.spaces_headphones_hint),
                    ) {
                        Switch(
                            checked = space.headphones,
                            onCheckedChange = {
                                update(space.copy(headphones = it))
                            },
                        )
                    }
                }
                item {
                    val hint = when (space.place) {
                        PlaceTrigger.HOME ->
                            if (placeHome == null) stringResource(R.string.spaces_set_home_hint) else null
                        PlaceTrigger.WORK ->
                            if (placeWork == null) stringResource(R.string.spaces_set_work_hint) else null
                        else -> null
                    }
                    AviateRow(
                        Icons.Filled.LocationOn,
                        stringResource(R.string.spaces_location),
                        value = when (space.place) {
                            PlaceTrigger.NONE -> stringResource(R.string.spaces_none)
                            PlaceTrigger.HOME -> stringResource(R.string.spaces_loc_home)
                            PlaceTrigger.WORK -> stringResource(R.string.spaces_loc_work)
                            else -> stringResource(R.string.spaces_loc_away)
                        } + (hint?.let { " · $it" } ?: ""),
                        onClick = { placeTriggerDialog = space.id },
                    )
                }
                item {
                    AviateRow(
                        Icons.Filled.DirectionsCar,
                        stringResource(R.string.spaces_driving),
                        subtitle = stringResource(R.string.spaces_driving_hint),
                    ) {
                        Switch(
                            checked = space.car,
                            onCheckedChange = { on ->
                                if (on && !hasBtPermission(context)) {
                                    pendingCarAfterGrant = true
                                    permLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                        ),
                                    )
                                }
                                update(space.copy(car = on))
                            },
                        )
                    }
                }

                item { SectionBand(stringResource(R.string.spaces_on_activate)) }
                item {
                    AviateRow(
                        Icons.Filled.Widgets,
                        stringResource(R.string.spaces_show_page),
                    ) {
                        Switch(
                            checked = space.showOnActivate,
                            onCheckedChange = {
                                update(space.copy(showOnActivate = it))
                            },
                        )
                    }
                }
                item {
                    val appLabel = space.launchOnActivate
                        ?.let { key ->
                            apps.firstOrNull { it.componentKey == key }?.label
                        } ?: stringResource(R.string.spaces_none)
                    AviateRow(
                        Icons.Filled.Add,
                        stringResource(R.string.spaces_open_app),
                        value = appLabel,
                        onClick = { appPickSpace = space.id },
                    )
                }

                item { SectionBand(stringResource(R.string.spaces_content)) }
                item {
                    AviateRow(
                        Icons.Filled.Star,
                        stringResource(R.string.spaces_apps_row),
                        value = "${
                            controller.shortcuts.value.count {
                                it.screenId == spaceScreenId(space.id)
                            }
                        } apps",
                        onClick = { showAppsDialog = true },
                    )
                }
                item {
                    AviateRow(
                        Icons.Filled.Widgets,
                        stringResource(R.string.add_widget),
                        onClick = { onAddWidgetToSpace(space.id) },
                    )
                }
                if (list.size > 1) {
                    item {
                        AviateRow(
                            Icons.Filled.Delete,
                            stringResource(R.string.spaces_delete),
                            onClick = { showDeleteConfirm = space.id },
                        )
                    }
                }
            }
        }
    }

    BackHandler(enabled = editing != null) { editingId = null }

    // ---- Dialogs ----

    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            title = { Text(stringResource(R.string.spaces_fallback_row)) },
            text = {
                Column {
                    list.forEach { s ->
                        RadioOption(
                            label = s.name,
                            selected = fallback == s.id,
                        ) {
                            scope.launch { settings.setSpaceFallback(s.id) }
                            showFallbackDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFallbackDialog = false }) {
                    Text(stringResource(R.string.spaces_close))
                }
            },
        )
    }

    placeDialog?.let { which ->
        val current = if (which == "home") placeHome else placeWork
        var latText by remember(which) {
            mutableStateOf(current?.lat?.toString() ?: "")
        }
        var lngText by remember(which) {
            mutableStateOf(current?.lng?.toString() ?: "")
        }
        var radius by remember(which) {
            mutableStateOf(current?.radiusM ?: 200)
        }
        LaunchedEffect(placeFill) {
            placeFill?.let {
                latText = it.lat.toString()
                lngText = it.lng.toString()
                placeFill = null
            }
        }
        val title = if (which == "home") {
            stringResource(R.string.spaces_place_home)
        } else {
            stringResource(R.string.spaces_place_work)
        }
        AlertDialog(
            onDismissRequest = { placeDialog = null },
            title = { Text(title) },
            text = {
                Column {
                    TextButton(onClick = {
                        if (!hasLocationPermission(context)) {
                            pendingPlaceAfterGrant = true
                            permLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        } else {
                            scope.launch {
                                engine.currentLocation()?.let { loc ->
                                    latText = loc.lat.toString()
                                    lngText = loc.lng.toString()
                                }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.spaces_use_current))
                    }
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.spaces_latitude)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text(stringResource(R.string.spaces_longitude)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.spaces_radius), modifier = Modifier.padding(top = 8.dp))
                    Row {
                        listOf(100, 200, 500, 1000).forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = radius == r,
                                    onClick = { radius = r },
                                )
                                Text("$r m")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lng = lngText.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        val p = SavedPlace(lat, lng, radius)
                        scope.launch {
                            if (which == "home") settings.setPlaceHome(p)
                            else settings.setPlaceWork(p)
                        }
                    }
                    placeDialog = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (which == "home") settings.setPlaceHome(null)
                        else settings.setPlaceWork(null)
                    }
                    placeDialog = null
                }) { Text(stringResource(R.string.spaces_clear)) }
            },
        )
    }

    if (showCarDialog) {
        val bonded = remember { engine.bondedDevices() }
        AlertDialog(
            onDismissRequest = { showCarDialog = false },
            title = { Text(stringResource(R.string.spaces_car_bt)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (bonded.isEmpty()) Text(stringResource(R.string.spaces_no_bonded))
                    bonded.forEach { (addr, name) ->
                        val checked = carDevices.any { it.first == addr }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = if (checked) {
                                        carDevices.filter { it.first != addr }
                                    } else {
                                        carDevices + (addr to name)
                                    }
                                    scope.launch {
                                        settings.setCarBtDevices(next)
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(
                                if (checked) Icons.Filled.CheckBox
                                else Icons.Filled.CheckBoxOutlineBlank,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(name)
                                Text(addr, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCarDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    }

    renameSpace?.let { id ->
        val space = list.firstOrNull { it.id == id } ?: return@let
        var name by remember(id) { mutableStateOf(space.name) }
        AlertDialog(
            onDismissRequest = { renameSpace = null },
            title = { Text(stringResource(R.string.spaces_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) update(space.copy(name = name.trim()))
                    renameSpace = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameSpace = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    iconPickSpace?.let { id ->
        val space = list.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { iconPickSpace = null },
            title = { Text(stringResource(R.string.spaces_icon)) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(240.dp),
                ) {
                    gridItems(SpaceGlyph.entries.toList()) { g ->
                        Icon(
                            imageVector = g.icon,
                            contentDescription = g.name,
                            tint = if (g == space.glyph) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                rowIconColor()
                            },
                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp)
                                .clickable {
                                    update(space.copy(glyph = g))
                                    iconPickSpace = null
                                },
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    timePick?.let { which ->
        val space = list.firstOrNull { it.id == editingId }
        val tw = space?.time ?: TimeWindow(0, 0)
        val initial = if (which == "start") tw.startMinute else tw.endMinute
        TimePickDialog(
            initial = initial,
            onPick = { m ->
                if (space != null) {
                    update(
                        space.copy(
                            time = if (which == "start") {
                                tw.copy(startMinute = m)
                            } else {
                                tw.copy(endMinute = m)
                            },
                        ),
                    )
                }
                timePick = null
            },
            onDismiss = { timePick = null },
        )
    }

    placeTriggerDialog?.let { id ->
        val space = list.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { placeTriggerDialog = null },
            title = { Text(stringResource(R.string.spaces_location)) },
            text = {
                Column {
                    PlaceTrigger.entries.forEach { p ->
                        val label = when (p) {
                            PlaceTrigger.NONE -> stringResource(R.string.spaces_none)
                            PlaceTrigger.HOME -> stringResource(R.string.spaces_loc_home)
                            PlaceTrigger.WORK -> stringResource(R.string.spaces_loc_work)
                            PlaceTrigger.AWAY ->
                                stringResource(R.string.spaces_loc_away)
                        }
                        RadioOption(
                            label = label,
                            selected = space.place == p,
                        ) {
                            if (p != PlaceTrigger.NONE &&
                                !hasLocationPermission(context)
                            ) {
                                pendingPlaceAfterGrant = true
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission
                                            .ACCESS_COARSE_LOCATION,
                                        Manifest.permission
                                            .ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                            update(space.copy(place = p))
                            placeTriggerDialog = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { placeTriggerDialog = null }) {
                    Text(stringResource(R.string.spaces_close))
                }
            },
        )
    }

    appPickSpace?.let { id ->
        val space = list.firstOrNull { it.id == id } ?: return@let
        var query by remember(id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { appPickSpace = null },
            title = { Text(stringResource(R.string.spaces_open_app)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RadioOption(
                        label = stringResource(R.string.spaces_none),
                        selected = space.launchOnActivate == null,
                    ) {
                        update(space.copy(launchOnActivate = null))
                        appPickSpace = null
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        lazyItems(
                            apps.filter {
                                query.isBlank() ||
                                    it.label.contains(query, true)
                            },
                            key = { it.componentKey },
                        ) { app ->
                            RadioOption(
                                label = app.label,
                                selected =
                                    space.launchOnActivate == app.componentKey,
                            ) {
                                update(
                                    space.copy(
                                        launchOnActivate = app.componentKey,
                                    ),
                                )
                                appPickSpace = null
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { appPickSpace = null }) {
                    Text(stringResource(R.string.spaces_close))
                }
            },
        )
    }

    if (showAppsDialog && editing != null) {
        val space = editing
        val screenShortcuts = controller.shortcuts.value
            .filter { it.screenId == spaceScreenId(space.id) }
            .map { it.componentKey }.toSet()
        var checked by remember(space.id) {
            mutableStateOf(screenShortcuts)
        }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppsDialog = false },
            title = { Text("Apps on ${space.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_apps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        lazyItems(
                            apps.filter {
                                query.isBlank() ||
                                    it.label.contains(query, true)
                            }.sortedBy { it.label.lowercase() },
                            key = { it.componentKey },
                        ) { app ->
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked =
                                            if (app.componentKey in checked) {
                                                checked - app.componentKey
                                            } else {
                                                checked + app.componentKey
                                            }
                                    }
                                    .padding(vertical = 6.dp),
                            ) {
                                Icon(
                                    if (app.componentKey in checked) {
                                        Icons.Filled.CheckBox
                                    } else {
                                        Icons.Filled.CheckBoxOutlineBlank
                                    },
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(app.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.setScreenApps(
                        spaceScreenId(space.id),
                        checked.toList(),
                    )
                    showAppsDialog = false
                }) { Text(stringResource(R.string.done)) }
            },
        )
    }

    showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.spaces_delete_title)) },
            text = {
                Text(stringResource(R.string.spaces_delete_msg))
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.removeScreenContent(spaceScreenId(id))
                    scope.launch {
                        settings.setSpaces(list.filterNot { it.id == id })
                        if (fallback == id) {
                            settings.setSpaceFallback(
                                list.firstOrNull { it.id != id }?.id ?: 0,
                            )
                        }
                        if (resolution?.spaceId == id &&
                            resolution?.manual == true
                        ) {
                            settings.setSpaceManual(null)
                        }
                    }
                    showDeleteConfirm = null
                    editingId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    initial: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_time_picker)) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
