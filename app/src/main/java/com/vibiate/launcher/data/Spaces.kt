package com.vibiate.launcher.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Glyph choices for a space (icon shown in the chip + top bar). */
enum class SpaceGlyph(val icon: ImageVector) {
    SUN(Icons.Filled.WbSunny),
    WORK(Icons.Filled.Work),
    CAR(Icons.Filled.DirectionsCar),
    HEADPHONES(Icons.Filled.Headphones),
    HOME(Icons.Filled.Home),
    NIGHT(Icons.Filled.NightsStay),
    FITNESS(Icons.Filled.FitnessCenter),
    STAR(Icons.Filled.Star),
}

/** Location-based trigger. AWAY = location known and outside every SET place. */
enum class PlaceTrigger { NONE, HOME, WORK, AWAY }

/** Whether any one configured trigger or all of them must hold. */
enum class TriggerMatch { ANY, ALL }

/**
 * Daily time window. [startMinute]/[endMinute] are minutes of day (0..1439);
 * equal means all day. [days] is a bitmask, bit0 = Monday … bit6 = Sunday.
 */
data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    val days: Int = 0x7F,
) {
    fun matches(now: LocalDateTime): Boolean {
        val todayBit = 1 shl (now.dayOfWeek.value - 1)
        val minute = now.hour * 60 + now.minute
        if (startMinute == endMinute) return days and todayBit != 0
        if (startMinute < endMinute) {
            return days and todayBit != 0 && minute in startMinute until endMinute
        }
        // Wraps midnight: "today" after start, or "yesterday" before end.
        if (minute >= startMinute) return days and todayBit != 0
        if (minute < endMinute) {
            val yesterdayBit = 1 shl ((now.dayOfWeek.value + 5) % 7)
            return days and yesterdayBit != 0
        }
        return false
    }

    fun serialize(): String = "$startMinute;$endMinute;$days"

    companion object {
        fun deserialize(s: String): TimeWindow? {
            val p = s.split(';')
            if (p.size < 3) return null
            return TimeWindow(
                p[0].toIntOrNull()?.coerceIn(0, 1439) ?: return null,
                p[1].toIntOrNull()?.coerceIn(0, 1439) ?: return null,
                p[2].toIntOrNull()?.coerceIn(0, 0x7F) ?: 0x7F,
            )
        }
    }
}

/** A saved place (Home/Work) with a trigger radius. */
data class SavedPlace(val lat: Double, val lng: Double, val radiusM: Int = 200) {
    fun serialize(): String = "$lat;$lng;$radiusM"

    companion object {
        fun deserialize(s: String): SavedPlace? {
            val p = s.split(';')
            if (p.size < 3) return null
            return SavedPlace(
                p[0].toDoubleOrNull() ?: return null,
                p[1].toDoubleOrNull() ?: return null,
                p[2].toIntOrNull() ?: 200,
            )
        }
    }
}

/** A location fix, reduced to what matching needs. */
data class LatLng(val lat: Double, val lng: Double, val accuracyM: Float = 0f)

data class SpaceDef(
    val id: Int,
    val name: String,
    val glyph: SpaceGlyph,
    val autoEnabled: Boolean = true,
    val match: TriggerMatch = TriggerMatch.ANY,
    val time: TimeWindow? = null,
    val headphones: Boolean = false,
    val place: PlaceTrigger = PlaceTrigger.NONE,
    val car: Boolean = false,
    /** Jump to the Spaces page when this space activates. */
    val showOnActivate: Boolean = false,
    /** componentKey of an app to launch when this space activates. */
    val launchOnActivate: String? = null,
) {
    private fun placeMatches(signals: SpaceSignals): Boolean {
        val loc = signals.location ?: return false
        return when (place) {
            PlaceTrigger.NONE -> false
            PlaceTrigger.HOME -> signals.home?.let {
                distanceMeters(loc, LatLng(it.lat, it.lng)) <=
                    it.radiusM + minOf(loc.accuracyM, 500f)
            } ?: false
            PlaceTrigger.WORK -> signals.work?.let {
                distanceMeters(loc, LatLng(it.lat, it.lng)) <=
                    it.radiusM + minOf(loc.accuracyM, 500f)
            } ?: false
            PlaceTrigger.AWAY -> {
                val inHome = signals.home?.let {
                    distanceMeters(loc, LatLng(it.lat, it.lng)) <=
                        it.radiusM + minOf(loc.accuracyM, 500f)
                } ?: false
                val inWork = signals.work?.let {
                    distanceMeters(loc, LatLng(it.lat, it.lng)) <=
                        it.radiusM + minOf(loc.accuracyM, 500f)
                } ?: false
                (signals.home != null || signals.work != null) && !inHome && !inWork
            }
        }
    }

    /** Reasons this space currently matches; empty when it doesn't. */
    fun matchReasons(signals: SpaceSignals): List<SpaceReason> {
        val reasons = mutableListOf<SpaceReason>()
        if (time != null && time.matches(signals.now)) reasons += SpaceReason.TIME
        if (headphones && signals.headphones) reasons += SpaceReason.HEADPHONES
        if (place != PlaceTrigger.NONE && placeMatches(signals)) {
            reasons += SpaceReason.PLACE
        }
        if (car && signals.carConnected) reasons += SpaceReason.CAR
        return reasons
    }

    fun matches(signals: SpaceSignals): Boolean {
        val configured = listOfNotNull(
            time?.let { t -> { t.matches(signals.now) } },
            if (headphones) ({ signals.headphones }) else null,
            if (place != PlaceTrigger.NONE) ({ placeMatches(signals) }) else null,
            if (car) ({ signals.carConnected }) else null,
        )
        if (configured.isEmpty()) return false
        return when (match) {
            TriggerMatch.ANY -> configured.any { it() }
            TriggerMatch.ALL -> configured.all { it() }
        }
    }

    /**
     * "s1|id|name|glyph|autoEnabled|match|timeStart|timeEnd|timeDays|
     * headphones|place|car|showOnActivate|launchKey". Empty time fields mean
     * null, empty launchKey means null. '|', '\n', '\r' in name -> spaces.
     */
    fun serialize(): String = buildString {
        append("s1|").append(id).append('|')
        append(name.replace('|', ' ').replace('\n', ' ').replace('\r', ' '))
        append('|').append(glyph.name)
        append('|').append(autoEnabled)
        append('|').append(match.name)
        append('|').append(time?.startMinute ?: "")
        append('|').append(time?.endMinute ?: "")
        append('|').append(time?.days ?: "")
        append('|').append(headphones)
        append('|').append(place.name)
        append('|').append(car)
        append('|').append(showOnActivate)
        append('|').append(launchOnActivate ?: "")
    }

    companion object {
        fun deserialize(s: String): SpaceDef? {
            val p = s.split('|')
            if (p.size < 14 || p[0] != "s1") return null
            val id = p[1].toIntOrNull() ?: return null
            val name = p[2].ifBlank { return null }
            val glyph = runCatching { SpaceGlyph.valueOf(p[3]) }
                .getOrDefault(SpaceGlyph.STAR)
            val autoEnabled = p[4].toBooleanStrictOrNull() ?: true
            val match = runCatching { TriggerMatch.valueOf(p[5]) }
                .getOrDefault(TriggerMatch.ANY)
            val time = if (p[6].isNotBlank() && p[7].isNotBlank()) {
                TimeWindow(
                    p[6].toIntOrNull() ?: return null,
                    p[7].toIntOrNull() ?: return null,
                    p[8].toIntOrNull() ?: 0x7F,
                )
            } else {
                null
            }
            val headphones = p[9].toBooleanStrictOrNull() ?: false
            val place = runCatching { PlaceTrigger.valueOf(p[10]) }
                .getOrDefault(PlaceTrigger.NONE)
            val car = p[11].toBooleanStrictOrNull() ?: false
            val showOnActivate = p[12].toBooleanStrictOrNull() ?: false
            val launchKey = p[13].ifBlank { null }
            return SpaceDef(
                id, name, glyph, autoEnabled, match, time, headphones,
                place, car, showOnActivate, launchKey,
            )
        }
    }
}

/** Live signal snapshot the resolver works on. */
data class SpaceSignals(
    val now: LocalDateTime,
    val headphones: Boolean,
    val carConnected: Boolean,
    val location: LatLng?,
    val home: SavedPlace?,
    val work: SavedPlace?,
)

/** A manual chip pick, stored with the auto winner seen at pick time. */
data class ManualPick(val spaceId: Int, val autoIdAtPick: Int?) {
    fun serialize(): String = "$spaceId;${autoIdAtPick ?: ""}"

    companion object {
        fun deserialize(s: String): ManualPick? {
            val p = s.split(';')
            if (p.size < 2 || p[0].isBlank()) return null
            return ManualPick(
                p[0].toIntOrNull() ?: return null,
                p[1].toIntOrNull(),
            )
        }
    }
}

enum class SpaceReason { TIME, HEADPHONES, PLACE, CAR, MANUAL, FALLBACK }

data class SpaceResolution(
    val spaceId: Int,
    val autoId: Int?,
    val manual: Boolean,
    val reasons: List<SpaceReason>,
    /** A stale manual pick was ignored — caller should clear the pref. */
    val manualStale: Boolean,
)

/** Widget/shortcut screenId for a space (Home is -1, Widgets 0, custom >0). */
fun spaceScreenId(spaceId: Int): Int = -100 - spaceId

/** Haversine great-circle distance between two points, in meters. */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6371000.0
    val dLat = (b.lat - a.lat) * PI / 180.0
    val dLng = (b.lng - a.lng) * PI / 180.0
    val h = sin(dLat / 2).pow(2) +
        cos(a.lat * PI / 180.0) * cos(b.lat * PI / 180.0) *
        sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/**
 * Resolve which space is effective. Auto winner = first space in list order
 * (priority) with autoEnabled that matches; null when auto-switching is off
 * or nothing matches. A manual pick holds while the auto winner is unchanged
 * since the pick (or auto-switching is off); a stale pick falls through to
 * the auto winner, then the fallback, then the first space.
 */
fun resolveSpaces(
    spaces: List<SpaceDef>,
    signals: SpaceSignals,
    autoSwitch: Boolean,
    fallbackId: Int,
    manual: ManualPick?,
): SpaceResolution {
    val autoWinner = if (autoSwitch) {
        spaces.firstOrNull { it.autoEnabled && it.matches(signals) }
    } else {
        null
    }
    val autoId = autoWinner?.id

    if (manual != null && spaces.any { it.id == manual.spaceId } &&
        (!autoSwitch || manual.autoIdAtPick == autoId)
    ) {
        return SpaceResolution(
            manual.spaceId, autoId, manual = true,
            reasons = listOf(SpaceReason.MANUAL), manualStale = false,
        )
    }
    val manualStale = manual != null

    if (autoWinner != null) {
        return SpaceResolution(
            autoWinner.id, autoId, manual = false,
            reasons = autoWinner.matchReasons(signals), manualStale = manualStale,
        )
    }
    val fallback = spaces.firstOrNull { it.id == fallbackId } ?: spaces.first()
    return SpaceResolution(
        fallback.id, autoId, manual = false,
        reasons = listOf(SpaceReason.FALLBACK), manualStale = manualStale,
    )
}

/**
 * The shipped spaces in priority order (list order wins): Listening,
 * Going Somewhere, Work, Morning. [names] supplies localized labels keyed
 * by id; missing entries get "Space N".
 */
fun defaultSpaces(names: (Int) -> String): List<SpaceDef> = listOf(
    // Going Somewhere outranks Listening: a car stereo is an A2DP output,
    // so in a car both would match.
    SpaceDef(
        id = 3, name = names(3), glyph = SpaceGlyph.CAR, car = true,
    ),
    SpaceDef(
        id = 4, name = names(4), glyph = SpaceGlyph.HEADPHONES,
        headphones = true,
    ),
    SpaceDef(
        id = 2, name = names(2), glyph = SpaceGlyph.WORK,
        time = TimeWindow(9 * 60, 18 * 60, days = 0x1F), // Mon–Fri
        place = PlaceTrigger.WORK,
    ),
    SpaceDef(
        id = 1, name = names(1), glyph = SpaceGlyph.SUN,
        time = TimeWindow(5 * 60, 11 * 60),
    ),
)

/** The default fallback space id (Morning). */
const val DEFAULT_SPACE_FALLBACK = 1

/**
 * Apps seeded on a fresh space: up to 8 componentKeys sorted by label.
 * Morning -> calendar/clock/weather packages or NEWS; Work -> PRODUCTIVITY;
 * Going Somewhere -> TRANSIT; Listening -> MUSIC.
 */
fun defaultSpaceApps(spaceId: Int, apps: List<AppEntry>): List<String> {
    val chosen = when (spaceId) {
        1 -> apps.filter {
            it.packageName.contains("calendar") ||
                it.packageName.contains("clock") ||
                it.packageName.contains("weather") ||
                it.category == AppCategory.NEWS
        }
        2 -> apps.filter { it.category == AppCategory.PRODUCTIVITY }
        3 -> apps.filter { it.category == AppCategory.TRANSIT }
        4 -> apps.filter { it.category == AppCategory.MUSIC }
        else -> emptyList()
    }
    return chosen.sortedBy { it.label.lowercase() }
        .take(8)
        .map { it.componentKey }
}
