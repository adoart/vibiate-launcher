package com.vibiate.launcher.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Fixed glyph palette for user-created categories. */
enum class CategoryGlyph(val icon: ImageVector) {
    FOLDER(Icons.Filled.Folder),
    STAR(Icons.Filled.Star),
    FAVORITE(Icons.Filled.Favorite),
    WORK(Icons.Filled.Work),
    HOME(Icons.Filled.Home),
    SCHOOL(Icons.Filled.School),
    SHOPPING(Icons.Filled.ShoppingCart),
    FOOD(Icons.Filled.Restaurant),
    FITNESS(Icons.Filled.FitnessCenter),
    TRAVEL(Icons.Filled.Flight),
    PETS(Icons.Filled.Pets),
    BOOKMARK(Icons.Filled.Bookmark),
}

/**
 * A user-created collection category. Category keys are strings: built-ins use
 * the enum name ("PRODUCTIVITY"), custom ones use "custom:<id>". Serialized as
 * "id;GLYPHNAME;name".
 */
data class UserCategory(val id: Int, val name: String, val glyph: CategoryGlyph) {
    val key get() = "custom:$id"

    fun serialize(): String =
        "$id;${glyph.name};${name.replace(';', ',').replace('\n', ' ')}"

    companion object {
        fun deserialize(s: String): UserCategory? {
            // At most 3 parts so the name is kept verbatim.
            val parts = s.split(';', limit = 3)
            if (parts.size < 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            val glyph = runCatching { CategoryGlyph.valueOf(parts[1]) }
                .getOrDefault(CategoryGlyph.FOLDER)
            val name = parts[2]
            if (name.isBlank()) return null
            return UserCategory(id, name, glyph)
        }
    }
}

/** Display model for one collection card: built-in or user-created. */
data class CollectionDef(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val isCustom: Boolean,
)

/**
 * Ordered (def, apps) pairs for the Collections page: custom categories first
 * (in list order, shown even when empty), then non-empty built-ins in
 * [AppCategory.ordered] order. Apps keep their incoming label sort.
 *
 * An app's effective category is `overrides[componentKey]` when that key names
 * a built-in or an existing custom category; otherwise its automatic
 * [AppEntry.category] (stale overrides fall back to auto).
 *
 * [categoryOrder] overrides the card order: listed keys come first in that
 * order (stale keys skipped), the rest keep their default relative order —
 * so a category created after a reorder lands at the end. [appOrder] pins
 * apps inside a card: listed componentKeys come first by list index, the rest
 * keep the incoming label sort.
 */
fun buildCollections(
    apps: List<AppEntry>,
    custom: List<UserCategory>,
    overrides: Map<String, String>,
    categoryOrder: List<String> = emptyList(),
    appOrder: Map<String, List<String>> = emptyMap(),
    hidden: Set<String> = emptySet(),
    extras: Map<String, List<String>> = emptyMap(),
): List<Pair<CollectionDef, List<AppEntry>>> {
    val byKeyOf = apps.associateBy { it.componentKey }
    val customKeys = custom.mapTo(HashSet()) { it.key }
    val builtInNames = AppCategory.entries.mapTo(HashSet()) { it.name }
    val known = customKeys + builtInNames

    fun effectiveKey(app: AppEntry): String {
        val override = overrides[app.componentKey]
        return if (override != null && override in known) {
            override
        } else {
            app.category.name
        }
    }

    val byKey = mutableMapOf<String, MutableList<AppEntry>>()
    apps.forEach { app ->
        if (app.componentKey !in hidden) {
            byKey.getOrPut(effectiveKey(app)) { mutableListOf() } += app
        }
    }
    // Extra memberships: stale/unknown category keys ignored; an app never
    // appears twice in the same card.
    extras.forEach { (cat, keys) ->
        if (cat !in known) return@forEach
        keys.forEach { k ->
            byKeyOf[k]?.let { app ->
                val list = byKey.getOrPut(cat) { mutableListOf() }
                if (app !in list) list += app
            }
        }
    }

    val default = mutableListOf<Pair<CollectionDef, List<AppEntry>>>()
    custom.forEach { cat ->
        default += CollectionDef(
            key = cat.key,
            title = cat.name,
            icon = cat.glyph.icon,
            isCustom = true,
        ) to (byKey[cat.key] ?: emptyList())
    }
    AppCategory.ordered.forEach { cat ->
        val list = byKey[cat.name].orEmpty()
        if (list.isNotEmpty()) {
            default += CollectionDef(
                key = cat.name,
                title = cat.title,
                icon = cat.icon,
                isCustom = false,
            ) to list
        }
    }

    val ordered = categoryOrder
        .mapNotNull { k -> default.firstOrNull { it.first.key == k } } +
        default.filter { it.first.key !in categoryOrder.toSet() }

    return ordered.map { (def, list) ->
        val pinned = appOrder[def.key]
        if (pinned.isNullOrEmpty()) {
            def to list
        } else {
            val indexOf = pinned.withIndex().associate { it.value to it.index }
            def to list.sortedBy { indexOf[it.componentKey] ?: Int.MAX_VALUE }
        }
    }
}

/** [item] reinserted at [index] (clamped), removed first if already present. */
fun <T> List<T>.withMoved(item: T, index: Int): List<T> {
    val list = toMutableList()
    list.remove(item)
    list.add(index.coerceIn(0, list.size), item)
    return list
}

// ---- Multi-card membership (Part B) ----

/**
 * The persisted category state a membership transform acts on: primary
 * overrides, extra memberships, the hidden set (removed-from-primary only)
 * and per-card pinned app order.
 */
data class CategoryState(
    val overrides: Map<String, String>,
    val extras: Map<String, List<String>>,
    val hidden: Set<String>,
    val appOrder: Map<String, List<String>>,
)

private fun effectivePrimary(state: CategoryState, key: String, autoKey: String, known: Set<String>): String {
    val o = state.overrides[key]
    return if (o != null && o in known) o else autoKey
}

private fun shownCards(state: CategoryState, key: String, autoKey: String, known: Set<String>): Set<String> {
    val cards = mutableSetOf<String>()
    if (key !in state.hidden) cards += effectivePrimary(state, key, autoKey, known)
    state.extras.forEach { (cat, keys) -> if (key in keys && cat in known) cards += cat }
    return cards
}

/** Add [key] to [target] (un-hide as needed) without leaving other cards. */
fun addMembership(state: CategoryState, key: String, autoKey: String, target: String, known: Set<String>): CategoryState {
    val primary = effectivePrimary(state, key, autoKey, known)
    // Target is the primary card: just un-hide (never also list it as extra).
    if (target == primary) return state.copy(hidden = state.hidden - key)
    if (shownCards(state, key, autoKey, known).isEmpty()) {
        // Shown nowhere: the target becomes the primary.
        val overrides = state.overrides.toMutableMap()
        if (target == autoKey) overrides.remove(key) else overrides[key] = target
        return state.copy(overrides = overrides, hidden = state.hidden - key)
    }
    val extras = state.extras.toMutableMap()
    extras[target] = ((extras[target] ?: emptyList()) + key).distinct()
    return state.copy(extras = extras, hidden = state.hidden - key)
}

/** Remove [key] from [source]; hiding the primary still keeps extras. */
fun removeMembership(state: CategoryState, key: String, autoKey: String, source: String, known: Set<String>): CategoryState {
    val order = state.appOrder.toMutableMap()
    order[source] = (order[source] ?: emptyList()) - key
    if (key in (state.extras[source] ?: emptyList())) {
        val extras = state.extras.toMutableMap()
        extras[source] = extras[source].orEmpty() - key
        return state.copy(extras = extras, appOrder = order)
    }
    val primary = effectivePrimary(state, key, autoKey, known)
    if (source == primary) {
        return state.copy(hidden = state.hidden + key, appOrder = order)
    }
    return state.copy(appOrder = order)
}

/** Move [key]'s membership from [source] to [target]. */
fun moveMembership(
    state: CategoryState,
    key: String,
    autoKey: String,
    source: String,
    target: String,
    known: Set<String>,
): CategoryState {
    if (source == target) return state
    val primary = effectivePrimary(state, key, autoKey, known)
    return if (source == primary && key !in state.hidden) {
        // Moving the visible primary: target becomes the primary.
        val overrides = state.overrides.toMutableMap()
        if (target == autoKey) overrides.remove(key) else overrides[key] = target
        val extras = state.extras.toMutableMap()
        extras[target] = extras[target].orEmpty() - key
        val order = state.appOrder.toMutableMap()
        order[source] = (order[source] ?: emptyList()) - key
        state.copy(overrides = overrides, extras = extras, appOrder = order)
    } else {
        addMembership(
            removeMembership(state, key, autoKey, source, known),
            key, autoKey, target, known,
        )
    }
}
