package com.vibiate.launcher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vibiate.launcher.R
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.WeatherInfo
import com.vibiate.launcher.data.WeatherService
import com.vibiate.launcher.ui.components.rememberNow
import com.vibiate.launcher.ui.theme.LocalIsDark
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val homeTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val homeDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * Aviate-style minimal home page: large thin clock, date, a subtle weather row
 * (Open-Meteo, coarse location, DataStore-cached), and a Google search pill.
 */
@Composable
fun HomeScreen(settings: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cached weather renders instantly; a fresh fetch replaces it.
    val cachedWeather by settings.weather.collectAsState(initial = null)
    var freshWeather by remember { mutableStateOf<WeatherInfo?>(null) }
    val weather = freshWeather ?: cachedWeather

    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var loading by remember { mutableStateOf(false) }

    fun refreshWeather() {
        if (!permissionGranted || loading) return
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                lastKnownLocation(context)
                    ?.let { WeatherService.fetch(it.latitude, it.longitude) }
            }
            loading = false
            if (result != null) {
                freshWeather = result
                settings.setWeather(result)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) refreshWeather()
    }

    // Ask for coarse location the first time home appears.
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            permissionGranted = true
            refreshWeather()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val now = rememberNow()

        // Dark mode only: subtle shadow keeps text legible over bright
        // wallpaper (the wallpaper now shows through the launcher).
        val shadow = if (LocalIsDark.current) {
            Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
        } else {
            null
        }

        Spacer(Modifier.height(72.dp))

        // Large thin clock — the Aviate signature.
        Text(
            text = now.format(homeTimeFormat),
            fontSize = 68.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp,
            style = TextStyle(shadow = shadow),
        )
        Text(
            text = now.format(homeDateFormat),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
            style = TextStyle(shadow = shadow),
        )

        // Weather row: hidden when permission was denied and nothing is cached;
        // subtle "--" while granted but no reading yet. Tap refreshes.
        if (weather != null || permissionGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { refreshWeather() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (weather != null) {
                    Icon(
                        imageVector = WeatherService.iconFor(weather.weatherCode),
                        contentDescription = stringResource(
                            WeatherService.labelResFor(weather.weatherCode)
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${weather.temperatureC.roundToInt()}°",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(shadow = shadow),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(WeatherService.labelResFor(weather.weatherCode)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(shadow = shadow),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.WbCloudy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.weather_unavailable),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        SearchPill { openGoogleSearch(context) }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.swipe_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 20.dp),
        )
    }
}

/** Rounded full-width Google-style search pill: "G" left, hint, mic right. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Text(
                text = "G",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.search_hint),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(R.string.voice_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

/** NETWORK provider first, then GPS; null when no fix exists. */
@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    return runCatching {
        manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }.getOrNull()
}

/** Google app -> ACTION_WEB_SEARCH -> browser fallback. Never throws. */
private fun openGoogleSearch(context: Context) {
    val candidates = listOfNotNull(
        runCatching {
            context.packageManager.getLaunchIntentForPackage(
                "com.google.android.googlequicksearchbox"
            )
        }.getOrNull(),
        Intent(Intent.ACTION_WEB_SEARCH),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search")),
    )
    for (intent in candidates) {
        val started = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (started) return
    }
}
