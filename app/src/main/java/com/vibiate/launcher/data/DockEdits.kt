package com.vibiate.launcher.data

import android.content.ComponentName
import kotlin.math.abs

/** Dock capacity: two rows of five fixed spots. */
const val DOCK_MAX = 10

/** Canonical dock key — same "pkg/activity" format as AppEntry.componentKey. */
fun dockKey(cn: ComponentName): String = "${cn.packageName}/${cn.className}"

/**
 * Normalize a stored key to the canonical form: expands the short form
 * "pkg/.Main" to "pkg/pkg.Main". Returns null for malformed keys.
 */
fun normalizeDockKey(key: String): String? {
    val slash = key.indexOf('/')
    if (slash <= 0 || slash == key.length - 1) return null
    val pkg = key.substring(0, slash)
    val cls = key.substring(slash + 1)
    val full = if (cls.startsWith(".")) pkg + cls else cls
    return "$pkg/$full"
}

/**
 * Place [key] at [target] on a fixed list of dock spots (null = hole).
 *
 * [fromIndex] is its dock slot when the icon is moved within the dock; when
 * null, any slot already holding [key] is vacated, so an outside drop of a
 * docked app moves it instead of duplicating. Dropping on a hole puts the
 * icon there and moves nothing else. Dropping on an occupied spot shifts the
 * icons between target and the nearest hole (ties go to the higher index)
 * one step toward it. With no hole at all the dropped app replaces the icon
 * at [target].
 */
fun dockPlace(
    slots: List<String?>,
    key: String,
    target: Int,
    fromIndex: Int? = null,
): List<String?> {
    if (slots.isEmpty() || target !in slots.indices) return slots
    val out = slots.toMutableList()
    val from = fromIndex?.takeIf { it in slots.indices }
        ?: slots.indexOf(key).takeIf { it >= 0 }
    if (from == target) return slots
    if (from != null) out[from] = null
    if (out[target] == null) {
        out[target] = key
        return out
    }
    val hole = slots.indices
        .filter { out[it] == null }
        .maxByOrNull { -abs(it - target) * 100 + it }
    if (hole == null) {
        out[target] = key
        return out
    }
    if (hole > target) {
        for (i in hole downTo target + 1) out[i] = out[i - 1]
    } else {
        for (i in hole until target) out[i] = out[i + 1]
    }
    out[target] = key
    return out
}

/** Remove [key] from the dock, leaving a hole; unchanged when absent. */
fun dockRemove(slots: List<String?>, key: String): List<String?> =
    slots.map { if (it == key) null else it }

/** Persisted form: one line per slot, holes are empty lines. */
fun encodeDockApps(slots: List<String?>): String =
    List(DOCK_MAX) { slots.getOrNull(it) ?: "" }.joinToString("\n")

/**
 * Decode the stored value; null input means "never customized". Keys are
 * normalized, later duplicates become holes, and the result is exactly
 * [DOCK_MAX] slots — a legacy compact list maps to slots 0..n-1.
 */
fun decodeDockApps(raw: String?): List<String?>? {
    if (raw == null) return null
    val seen = HashSet<String>()
    val slots = raw.split("\n").map { line ->
        val k = line.trim().takeIf { it.isNotEmpty() }
            ?.let(::normalizeDockKey)
        if (k != null && seen.add(k)) k else null
    }
    return List(DOCK_MAX) { slots.getOrNull(it) }
}
