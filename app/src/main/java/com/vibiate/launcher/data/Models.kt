package com.vibiate.launcher.data

import android.content.ComponentName
import androidx.annotation.StringRes
import com.vibiate.launcher.R
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppCategory(val title: String, val icon: ImageVector) {
    PRODUCTIVITY("PRODUCTIVITY", Icons.Filled.Work),
    SOCIAL("SOCIAL", Icons.Filled.People),
    UTILITIES("UTILITIES", Icons.Filled.Build),
    MUSIC("MUSIC", Icons.Filled.MusicNote),
    TRANSIT("TRANSIT", Icons.Filled.DirectionsTransit),
    PHOTOGRAPHY("PHOTOGRAPHY", Icons.Filled.PhotoCamera),
    ENTERTAINMENT("ENTERTAINMENT", Icons.Filled.Movie),
    NEWS("NEWS", Icons.AutoMirrored.Filled.Article),
    GAMES("GAMES", Icons.Filled.SportsEsports),
    OTHER("OTHER", Icons.Filled.Apps);

    companion object {
        val ordered: List<AppCategory> = entries.toList()
    }
}

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val category: AppCategory,
) {
    val component: ComponentName get() = ComponentName(packageName, activityName)
    val componentKey: String get() = "$packageName/$activityName"
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide font family choice, applied through VibiateTheme's Typography. */
enum class LauncherFont(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.font_system),
    SANS_SERIF(R.string.font_sans_serif),
    SERIF(R.string.font_serif),
    MONOSPACE(R.string.font_monospace),
}

/** Last successful weather reading, persisted in DataStore so it renders instantly. */
data class WeatherInfo(
    val temperatureC: Double,
    val weatherCode: Int, // WMO weather interpretation code
    val fetchedAt: Long,
) {
    fun serialize(): String = "$temperatureC;$weatherCode;$fetchedAt"

    companion object {
        fun deserialize(s: String): WeatherInfo? {
            val parts = s.split(';')
            if (parts.size != 3) return null
            return WeatherInfo(
                parts[0].toDoubleOrNull() ?: return null,
                parts[1].toIntOrNull() ?: return null,
                parts[2].toLongOrNull() ?: return null,
            )
        }
    }
}

/**
 * One dock slot. [intent] is fully resolved (explicit component, NEW_TASK) and
 * ready to startActivity. When nothing resolved, [icon] is null and [glyph]
 * is the Material fallback; [intent] is then an empty no-op Intent.
 */
data class DockEntry(
    val label: String,
    val icon: Drawable?,
    val glyph: ImageVector,
    val intent: Intent,
    val component: ComponentName?,
)

data class IconPackInfo(val packageName: String, val label: String)

/**
 * One bound app widget. [screenId] is 0 for the built-in Widgets page or the id
 * of a [CustomScreen]. Serialized as "id;pkg;class;screenId"; legacy 3-field
 * lines (no screenId) decode as screenId 0.
 */
data class WidgetEntry(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val screenId: Int = 0,
) {
    fun serialize(): String = "$appWidgetId;$providerPackage;$providerClass;$screenId"
    val component: ComponentName get() = ComponentName(providerPackage, providerClass)

    companion object {
        fun deserialize(s: String): WidgetEntry? {
            val parts = s.split(';')
            if (parts.size !in 3..4) return null
            val id = parts[0].toIntOrNull() ?: return null
            // Missing or malformed screenId falls back to the built-in page.
            val screenId = parts.getOrNull(3)?.toIntOrNull() ?: 0
            return WidgetEntry(id, parts[1], parts[2], screenId)
        }
    }
}

/**
 * A user-created extra pager page (a widget space). Ids start at 1; 0 is
 * reserved for the built-in Widgets page. Serialized as "id;name".
 */
data class CustomScreen(
    val id: Int,
    val name: String,
) {
    fun serialize(): String = "$id;${name.replace(';', ',').replace('\n', ' ')}"

    companion object {
        fun deserialize(s: String): CustomScreen? {
            val sep = s.indexOf(';')
            if (sep <= 0) return null
            val id = s.substring(0, sep).toIntOrNull() ?: return null
            val name = s.substring(sep + 1)
            if (name.isBlank()) return null
            return CustomScreen(id, name)
        }
    }
}

enum class WallpaperInterval(val minutes: Long, val label: String) {
    M15(15, "15 minutes"),
    H1(60, "1 hour"),
    H6(360, "6 hours"),
    H12(720, "12 hours"),
    H24(1440, "24 hours");

    companion object {
        fun fromMinutes(m: Long): WallpaperInterval =
            entries.firstOrNull { it.minutes == m } ?: H24
    }
}
