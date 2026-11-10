package com.vibiate.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Watches the signals that drive Aviate Spaces (clock, headphones, car mode,
 * Bluetooth connections, location) and resolves the effective space through
 * the pure [resolveSpaces]. Listeners run only while started (the activity's
 * ON_START/ON_STOP lifecycle).
 */
class SpaceEngine(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)

    private val now = MutableStateFlow(LocalDateTime.now())
    private val headphones = MutableStateFlow(false)
    private val carMode = MutableStateFlow(false)
    private val connectedBt = MutableStateFlow<Set<String>>(emptySet())
    private val location = MutableStateFlow<LatLng?>(null)

    /** Latest resolution; null until settings + signals first combine. */
    private val _resolution = MutableStateFlow<SpaceResolution?>(null)
    val resolution: StateFlow<SpaceResolution?> = _resolution

    /**
     * Set on auto space switches (not manual picks, not the first) and held
     * until the UI [consumeActivation]s it: car mode is a uiMode config
     * change, so the switch lands while the activity is being recreated and
     * the old composition may be torn down mid-handling.
     */
    private val _pendingActivation = MutableStateFlow<SpaceDef?>(null)
    val pendingActivation: StateFlow<SpaceDef?> = _pendingActivation

    fun consumeActivation(def: SpaceDef) {
        _pendingActivation.compareAndSet(def, null)
    }

    // ---- Listeners ----

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val uiModeManager by lazy {
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    }
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private val btAdapter: BluetoothAdapter? by lazy {
        runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager).adapter
        }.getOrNull()
    }

    private val outputTypes = mutableSetOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HEARING_AID,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) =
            refreshHeadphones()

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) =
            refreshHeadphones()
    }

    private val carReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) =
            refreshCarMode()
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshBt()
    }

    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as BluetoothHeadset
            }
            refreshBt()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refreshBt()
        }
    }

    private val locationListener = LocationListener { loc -> applyFix(loc) }

    private var lastCurrentLocationFetch = 0L

    // ---- Lifecycle ----

    fun start() {
        if (!started.compareAndSet(false, true)) return
        now.value = LocalDateTime.now()
        runCatching {
            audioManager.registerAudioDeviceCallback(audioCallback, mainHandler)
        }
        refreshHeadphones()
        runCatching {
            ContextCompat.registerReceiver(
                context,
                carReceiver,
                IntentFilter().apply {
                    addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
                    addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        refreshCarMode()
        refreshBt()
        refreshLocation()
        ensureLocationUpdates()
        scope.launch {
            while (started.get()) {
                // Sleep until just after the next minute tick, then tick.
                delay(60_000L - (System.currentTimeMillis() % 60_000L) + 250L)
                if (!started.get()) break
                now.value = LocalDateTime.now()
                refreshCarMode()
            }
        }
        scope.launch { fetchCurrentLocationOnce() }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { audioManager.unregisterAudioDeviceCallback(audioCallback) }
        runCatching { context.unregisterReceiver(carReceiver) }
        runCatching { context.unregisterReceiver(btReceiver) }
        runCatching { locationManager.removeUpdates(locationListener) }
        locationUpdatesOn = false
        closeBtProxies()
    }

    /**
     * Re-check permission-dependent listeners after the user grants location
     * or Bluetooth permission at runtime.
     */
    fun refreshPermissions() {
        if (!started.get()) return
        refreshBt()
        refreshLocation()
        ensureLocationUpdates()
        scope.launch { fetchCurrentLocationOnce() }
    }

    private var locationUpdatesOn = false

    /** Passive updates — started once, while started and permitted. */
    @SuppressLint("MissingPermission")
    private fun ensureLocationUpdates() {
        if (locationUpdatesOn || !hasLocationPermission()) return
        locationUpdatesOn = true
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER, 60_000L, 50f,
                locationListener, Looper.getMainLooper(),
            )
        }.onFailure { locationUpdatesOn = false }
    }

    // ---- Signals ----

    private fun refreshHeadphones() {
        headphones.value = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in outputTypes }
    }

    private fun refreshCarMode() {
        carMode.value = runCatching {
            uiModeManager.currentModeType == android.content.res.Configuration
                .UI_MODE_TYPE_CAR
        }.getOrDefault(false)
    }

    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    private var btReceiverRegistered = false

    @SuppressLint("MissingPermission")
    private fun refreshBt() {
        if (!hasBtPermission()) {
            connectedBt.value = emptySet()
            return
        }
        val adapter = btAdapter ?: return
        if (a2dpProxy == null) {
            runCatching {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            }
        }
        if (headsetProxy == null) {
            runCatching {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            }
        }
        if (!btReceiverRegistered) {
            btReceiverRegistered = true
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    btReceiver,
                    IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                        addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        }
        val connected = mutableSetOf<String>()
        runCatching { connected += a2dpProxy?.connectedDevices?.map { it.address }.orEmpty() }
        runCatching { connected += headsetProxy?.connectedDevices?.map { it.address }.orEmpty() }
        connectedBt.value = connected
    }

    private fun closeBtProxies() {
        val adapter = btAdapter ?: return
        a2dpProxy?.let {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
        headsetProxy?.let {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }
        a2dpProxy = null
        headsetProxy = null
        btReceiverRegistered = false
    }

    /** Paired devices for the settings checklist: (address, name). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<Pair<String, String>> {
        if (!hasBtPermission()) return emptyList()
        return runCatching {
            btAdapter?.bondedDevices?.map { it.address to (it.name ?: it.address) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun refreshLocation() {
        if (!hasLocationPermission()) {
            location.value = null
            return
        }
        // Freshest known fix across providers; applied only when newer than
        // the fix we hold, so a stale location never sticks across starts.
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { p ->
            runCatching { locationManager.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }?.let(::applyFix)
    }

    private var locationFixTime = 0L

    private fun applyFix(loc: Location) {
        if (loc.time < locationFixTime) return
        locationFixTime = loc.time
        location.value = LatLng(loc.latitude, loc.longitude, loc.accuracy)
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocationOnce() {
        if (!hasLocationPermission()) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastCurrentLocationFetch < 120_000L) return
        lastCurrentLocationFetch = nowMs
        val provider = when {
            Build.VERSION.SDK_INT >= 31 &&
                runCatching {
                    locationManager.getProvider(LocationManager.FUSED_PROVIDER)
                }.getOrNull() != null -> LocationManager.FUSED_PROVIDER
            runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.GPS_PROVIDER
        }
        runCatching {
            @Suppress("DEPRECATION")
            locationManager.getCurrentLocation(provider, null, mainExecutor) { loc ->
                loc?.let(::applyFix)
            }
        }
    }

    private val mainExecutor =
        java.util.concurrent.Executor { r -> mainHandler.post(r) }

    /** Fresh fix for the "Use current location" button; null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val last = location.value
        val provider = when {
            Build.VERSION.SDK_INT >= 31 &&
                runCatching {
                    locationManager.getProvider(LocationManager.FUSED_PROVIDER)
                }.getOrNull() != null -> LocationManager.FUSED_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val fresh = suspendCancellableCoroutine<Location?> { cont ->
            runCatching {
                @Suppress("DEPRECATION")
                locationManager.getCurrentLocation(provider, null, mainExecutor) {
                    if (cont.isActive) cont.resume(it)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
        fresh?.let(::applyFix)
        return (fresh?.let { LatLng(it.latitude, it.longitude, it.accuracy) }
            ?: last)
    }

    // ---- Resolution ----

    init {
        scope.launch {
            val settingsFlow = combine(
                settings.spaces,
                settings.spacesAuto,
                settings.spaceFallback,
                settings.spaceManual,
                settings.placeHome,
                settings.placeWork,
                settings.carBtDevices,
            ) { arr -> arr }
            val signalsFlow = combine(
                now, headphones, carMode, connectedBt, location,
            ) { arr -> arr }
            // Only a signal change may emit an activation — settings edits
            // and manual picks must never launch a space's activate actions.
            var lastSignals: List<Any?>? = null
            combine(settingsFlow, signalsFlow) { s, sig ->
                @Suppress("UNCHECKED_CAST")
                val spaces = s[0] as List<SpaceDef>
                val auto = s[1] as Boolean
                val fallback = s[2] as Int
                val manual = s[3] as ManualPick?
                val home = s[4] as SavedPlace?
                val work = s[5] as SavedPlace?
                @Suppress("UNCHECKED_CAST")
                val carDevs = s[6] as List<Pair<String, String>>
                val nowV = sig[0] as LocalDateTime
                val hp = sig[1] as Boolean
                val carModeOn = sig[2] as Boolean
                @Suppress("UNCHECKED_CAST")
                val bt = sig[3] as Set<String>
                val loc = sig[4] as LatLng?
                val carAddrs = carDevs.map { it.first }.toSet()
                val car = carModeOn || bt.any { it in carAddrs }
                val signalsChanged = sig.toList() != lastSignals
                lastSignals = sig.toList()
                Triple(spaces, resolveSpaces(
                    spaces,
                    SpaceSignals(nowV, hp, car, loc, home, work),
                    auto, fallback, manual,
                ), signalsChanged)
            }.collect { (spaces, res, signalsChanged) ->
                val prev = _resolution.value
                _resolution.value = res
                if (res.manualStale) settings.setSpaceManual(null)
                if (prev != null && prev.spaceId != res.spaceId &&
                    !res.manual && signalsChanged
                ) {
                    spaces.firstOrNull { it.id == res.spaceId }?.let {
                        _pendingActivation.value = it
                    }
                }
            }
        }
    }

    /** Manual chip pick: null = back to automatic. */
    suspend fun selectManual(spaceId: Int?) {
        if (spaceId == null) {
            settings.setSpaceManual(null)
        } else {
            settings.setSpaceManual(
                ManualPick(spaceId, _resolution.value?.autoId)
            )
        }
    }
}
