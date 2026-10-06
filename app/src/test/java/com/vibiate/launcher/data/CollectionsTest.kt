package com.vibiate.launcher.data

import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        cat: AppCategory = AppCategory.OTHER,
    ): AppEntry = AppEntry(pkg, "MainActivity", label, ColorDrawable(), cat)

    private fun keys(
        collections: List<Pair<CollectionDef, List<AppEntry>>>,
    ): List<String> = collections.map { it.first.key }

    @Test
    fun `default order - custom first, empty custom shown, empty built-in hidden`() {
        val custom = listOf(
            UserCategory(1, "EMPTY", CategoryGlyph.FOLDER),
            UserCategory(2, "WORK", CategoryGlyph.WORK),
        )
        val apps = listOf(
            app("a", cat = AppCategory.PRODUCTIVITY),
            app("b", cat = AppCategory.SOCIAL),
            app("c", cat = AppCategory.SOCIAL),
        )
        val result = buildCollections(apps, custom, emptyMap())
        // No built-in MUSIC/… cards (empty), empty custom:1 still present.
        assertEquals(
            listOf("custom:1", "custom:2", "PRODUCTIVITY", "SOCIAL"),
            keys(result),
        )
        assertEquals(
            listOf("b", "c"),
            result[3].second.map { it.packageName },
        )
    }

    @Test
    fun `categoryOrder - listed keys first in order, rest appended, stale ignored`() {
        val custom = listOf(UserCategory(1, "WORK", CategoryGlyph.WORK))
        val apps = listOf(
            app("a", cat = AppCategory.PRODUCTIVITY),
            app("b", cat = AppCategory.SOCIAL),
            app("c", cat = AppCategory.MUSIC),
        )
        val result = buildCollections(
            apps,
            custom,
            emptyMap(),
            categoryOrder = listOf("MUSIC", "custom:1", "custom:stale"),
        )
        // MUSIC + custom:1 first in requested order, stale key skipped,
        // then PRODUCTIVITY + SOCIAL in default relative order.
        assertEquals(
            listOf("MUSIC", "custom:1", "PRODUCTIVITY", "SOCIAL"),
            keys(result),
        )
    }

    @Test
    fun `appOrder - pinned apps first by index, rest by label, stale ignored`() {
        val apps = listOf(
            app("a", "Alpha", AppCategory.SOCIAL),
            app("b", "Bravo", AppCategory.SOCIAL),
            app("c", "Charlie", AppCategory.SOCIAL),
        )
        val result = buildCollections(
            apps,
            emptyList(),
            emptyMap(),
            appOrder = mapOf("SOCIAL" to listOf("c/MainActivity", "stale/one")),
        )
        assertEquals(
            listOf("c", "a", "b"),
            result[0].second.map { it.packageName },
        )
    }

    @Test
    fun `override to deleted custom category falls back to auto`() {
        val apps = listOf(app("a", cat = AppCategory.MUSIC))
        val result = buildCollections(
            apps,
            emptyList(), // custom:9 no longer exists
            mapOf("a/MainActivity" to "custom:9"),
        )
        assertEquals(listOf("MUSIC"), keys(result))
        assertEquals("a", result[0].second.single().packageName)
    }

    @Test
    fun `withMoved - insert, move forward, move backward, clamp`() {
        val list = listOf("a", "b", "c")
        // New item inserted.
        assertEquals(listOf("a", "x", "b", "c"), list.withMoved("x", 1))
        // Forward move.
        assertEquals(listOf("b", "c", "a"), list.withMoved("a", 2))
        // Backward move.
        assertEquals(listOf("c", "a", "b"), list.withMoved("c", 0))
        // Out-of-range clamps into place.
        assertEquals(listOf("a", "b", "c"), list.withMoved("c", 99))
        assertEquals(listOf("x", "a", "b", "c"), list.withMoved("x", -5))
    }
}
