package com.vibiate.launcher.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.vibiate.launcher.R
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Minimal Open-Meteo client: free, no API key, plain [HttpURLConnection] +
 * [org.json.JSONObject]. Also owns the WMO weather-code -> icon/label mapping.
 */
object WeatherService {

    private const val FORECAST_URL =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=%f&longitude=%f" +
            "&current=temperature_2m,weather_code&timezone=auto"

    /**
     * Blocking network call — invoke from a background dispatcher.
     * Returns null on any failure (no connectivity, bad fix, bad JSON).
     */
    fun fetch(latitude: Double, longitude: Double): WeatherInfo? = runCatching {
        val url = FORECAST_URL.format(latitude, longitude)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            WeatherInfo(
                temperatureC = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** WMO weather interpretation code -> condition glyph. */
    fun iconFor(code: Int): ImageVector = when (code) {
        0 -> Icons.Filled.WbSunny
        1, 2 -> Icons.Filled.WbCloudy
        3 -> Icons.Filled.Cloud
        45, 48 -> Icons.Filled.Dehaze
        // Drizzle, freezing drizzle, rain, freezing rain, rain showers.
        in 51..57, in 61..67, in 80..82 -> Icons.Filled.Grain
        // Snow fall/grains and snow showers.
        in 71..77, 85, 86 -> Icons.Filled.AcUnit
        // Thunderstorm (with/without hail).
        in 95..99 -> Icons.Filled.Thunderstorm
        else -> Icons.Filled.WbCloudy
    }

    /** WMO weather interpretation code -> label string resource. */
    @StringRes
    fun labelResFor(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        51, 53, 55 -> R.string.weather_drizzle
        56, 57 -> R.string.weather_freezing_drizzle
        61, 63, 65 -> R.string.weather_rain
        66, 67 -> R.string.weather_freezing_rain
        71, 73, 75, 77 -> R.string.weather_snow
        80, 81, 82 -> R.string.weather_showers
        85, 86 -> R.string.weather_snow_showers
        95 -> R.string.weather_thunderstorm
        96, 99 -> R.string.weather_hail
        else -> R.string.weather_unknown
    }
}
