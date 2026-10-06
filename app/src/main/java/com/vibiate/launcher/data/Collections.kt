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
): List<Pair<CollectionDef, List<AppEntry>>> {
    val customKeys = custom.mapTo(HashSet()) { it.key }
    val builtInNames = AppCategory.entries.mapTo(HashSet()) { it.name }

    fun effectiveKey(app: AppEntry): String {
        val override = overrides[app.componentKey]
        return if (override != null && (override in customKeys || override in builtInNames)) {
            override
        } else {
            app.category.name
        }
    }

    val byKey = apps.groupBy(::effectiveKey)

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
        val list = byKey[cat.name] ?: emptyList()
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
