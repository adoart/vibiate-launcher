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

/** An app icon pinned to a widget page's grid at a fixed cell. */
data class PageShortcut(
    val id: Int,
    val componentKey: String,
    val screenId: Int,
    val col: Int = -1,
    val row: Int = -1,
) {
    /** "id;screenId;col;row;componentKey" — the key goes last. */
    fun serialize(): String = "$id;$screenId;$col;$row;$componentKey"

    companion object {
        fun deserialize(s: String): PageShortcut? {
            val parts = s.split(';', limit = 5)
            if (parts.size < 5) return null
            val id = parts[0].toIntOrNull() ?: return null
            val screenId = parts[1].toIntOrNull() ?: return null
            val col = parts[2].toIntOrNull() ?: return null
            val row = parts[3].toIntOrNull() ?: return null
            val key = normalizeDockKey(parts[4]) ?: return null
            return PageShortcut(id, key, screenId, col, row)
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide font family choice, applied through VibiateTheme's Typography. */
enum class LauncherFont(@param:StringRes val labelRes: Int) {
    AVIATE(R.string.font_aviate),
    SYSTEM(R.string.font_system),
    SANS_SERIF(R.string.font_sans_serif),
    SERIF(R.string.font_serif),
    MONOSPACE(R.string.font_monospace),
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
 * Reserved widget screen ids: 0 = built-in Widgets page, >0 = custom widget
 * spaces, -1 = the Home page.
 */
const val HOME_SCREEN_ID = -1

/**
 * One bound app widget. [screenId] is 0 for the built-in Widgets page or the id
 * of a [CustomScreen]. [cols]/[rows] are the user-chosen span in grid cells
 * (0 = auto from the provider's min/target sizes); [title] is an optional
 * label shown above the widget. Serialized as
 * "id;pkg;class;screenId;cols;rows;title"; legacy 3- and 4-field lines decode
 * with cols/rows 0 and an empty title.
 */
data class WidgetEntry(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val screenId: Int = 0,
    val cols: Int = 0,
    val rows: Int = 0,
    val title: String = "",
    /** Fixed grid position; -1 = not placed yet (migrated on render). */
    val col: Int = -1,
    val row: Int = -1,
) {
    /** v2 format: "v2;id;pkg;class;screenId;cols;rows;col;row;title". */
    fun serialize(): String =
        "v2;$appWidgetId;$providerPackage;$providerClass;$screenId;" +
            "$cols;$rows;$col;$row;" +
            title.replace('\n', ' ').replace('\r', ' ')
    val component: ComponentName get() = ComponentName(providerPackage, providerClass)

    companion object {
        fun deserialize(s: String): WidgetEntry? {
            if (s.startsWith("v2;")) {
                // Title is last so a ';' inside it survives the split.
                val parts = s.removePrefix("v2;").split(';', limit = 9)
                if (parts.size < 3) return null
                val id = parts[0].toIntOrNull() ?: return null
                return WidgetEntry(
                    appWidgetId = id,
                    providerPackage = parts[1],
                    providerClass = parts[2],
                    screenId = parts.getOrNull(3)?.toIntOrNull() ?: 0,
                    cols = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                    rows = parts.getOrNull(5)?.toIntOrNull() ?: 0,
                    title = parts.getOrNull(8) ?: "",
                    col = parts.getOrNull(6)?.toIntOrNull() ?: -1,
                    row = parts.getOrNull(7)?.toIntOrNull() ?: -1,
                )
            }
            // Legacy "id;pkg;class;screenId;cols;rows;title".
            val parts = s.split(';', limit = 7)
            if (parts.size < 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            // Missing or malformed fields fall back to defaults.
            val screenId = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val cols = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val rows = parts.getOrNull(5)?.toIntOrNull() ?: 0
            val title = parts.getOrNull(6) ?: ""
            return WidgetEntry(id, parts[1], parts[2], screenId, cols, rows, title)
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
