package com.vibiate.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vibiateDataStore by preferencesDataStore(name = "vibiate_settings")

/** All persisted launcher preferences, backed by Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val ICON_PACK_FALLBACKS = stringPreferencesKey("icon_pack_fallbacks") // newline-joined pkgs, ordered
        val ICON_MASK_UNTHEMED = booleanPreferencesKey("icon_mask_unthemed")
        val ICON_OVERRIDES = stringPreferencesKey("icon_overrides") // newline-joined "key=pkg|drawable" / "key=@original"
        val WP_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        val WP_INTERVAL = longPreferencesKey("wallpaper_interval_minutes")
        val WP_URIS = stringPreferencesKey("wallpaper_uris") // newline-joined, ordered
        val WP_INDEX = intPreferencesKey("wallpaper_index")
        val WIDGETS = stringPreferencesKey("widgets") // newline-joined WidgetEntry.serialize()
        val CUSTOM_SCREENS = stringPreferencesKey("custom_screens") // newline-joined CustomScreen.serialize()
        val HOME_WIDGETS_SEEDED = booleanPreferencesKey("home_widgets_seeded")
        val GRID_SIZE = intPreferencesKey("grid_size")
        val FONT_FAMILY = stringPreferencesKey("font_family") // LauncherFont.name
        val CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories") // newline-joined UserCategory.serialize()
        val CATEGORY_OVERRIDES = stringPreferencesKey("category_overrides") // newline-joined "componentKey=categoryKey"
        val CATEGORY_ORDER = stringPreferencesKey("category_order") // newline-joined category keys
        val APP_ORDER = stringPreferencesKey("app_order") // newline-joined "categoryKey=ck1|ck2|..."
    }

    private val store get() = context.vibiateDataStore

    // ---- Appearance ----

    val themeMode: Flow<ThemeMode> = store.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: "") }.getOrDefault(ThemeMode.DARK)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    // ---- Icon pack ----

    /** Empty string = system default icons. */
    val iconPackPackage: Flow<String> = store.data.map { it[Keys.ICON_PACK] ?: "" }

    suspend fun setIconPack(pkg: String) {
        store.edit {
            it[Keys.ICON_PACK] = pkg
            // A pack can't be primary and fallback at once.
            val fallbacks = it[Keys.ICON_PACK_FALLBACKS]?.split('\n')
                ?.filter { s -> s.isNotBlank() && s != pkg } ?: emptyList()
            it[Keys.ICON_PACK_FALLBACKS] = fallbacks.joinToString("\n")
        }
    }

    /** Fallback packs tried when the primary has no icon for an app. */
    val iconPackFallbacks: Flow<List<String>> = store.data.map {
        it[Keys.ICON_PACK_FALLBACKS]?.split('\n')?.filter { s -> s.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setIconPackFallbacks(packs: List<String>) {
        store.edit { it[Keys.ICON_PACK_FALLBACKS] = packs.joinToString("\n") }
    }

    /** Whether the primary pack's mask/back is composited under unthemed icons. */
    val maskUnthemedIcons: Flow<Boolean> = store.data.map {
        it[Keys.ICON_MASK_UNTHEMED] ?: true
    }

    suspend fun setMaskUnthemedIcons(enabled: Boolean) {
        store.edit { it[Keys.ICON_MASK_UNTHEMED] = enabled }
    }

    /** componentKey -> per-app icon choice. */
    val iconOverrides: Flow<Map<String, IconOverride>> = store.data.map {
        parseIconOverrides(it[Keys.ICON_OVERRIDES])
    }

    /** Set (or null to clear) one app's icon override. */
    suspend fun setIconOverride(componentKey: String, override: IconOverride?) {
        store.edit { p ->
            val map = parseIconOverrides(p[Keys.ICON_OVERRIDES]).toMutableMap()
            if (override == null) map.remove(componentKey)
            else map[componentKey] = override
            p[Keys.ICON_OVERRIDES] = serializeIconOverrides(map)
        }
    }

    // ---- Collection grid ----

    /** Apps per row in collection sections (collapsed row and expanded grid). */
    val gridSize: Flow<Int> = store.data.map { it[Keys.GRID_SIZE] ?: DEFAULT_GRID_SIZE }

    suspend fun setGridSize(columns: Int) {
        store.edit { it[Keys.GRID_SIZE] = columns }
    }

    // ---- Font ----

    val fontFamily: Flow<LauncherFont> = store.data.map {
        runCatching { LauncherFont.valueOf(it[Keys.FONT_FAMILY] ?: "") }
            .getOrDefault(LauncherFont.AVIATE)
    }

    suspend fun setFontFamily(font: LauncherFont) {
        store.edit { it[Keys.FONT_FAMILY] = font.name }
    }

    // ---- Wallpaper rotation ----

    val wallpaperEnabled: Flow<Boolean> = store.data.map { it[Keys.WP_ENABLED] ?: false }

    suspend fun setWallpaperEnabled(enabled: Boolean) {
        store.edit { it[Keys.WP_ENABLED] = enabled }
    }

    val wallpaperIntervalMinutes: Flow<Long> =
        store.data.map { it[Keys.WP_INTERVAL] ?: WallpaperInterval.H24.minutes }

    suspend fun setWallpaperInterval(minutes: Long) {
        store.edit { it[Keys.WP_INTERVAL] = minutes }
    }

    val wallpaperUris: Flow<List<String>> = store.data.map {
        it[Keys.WP_URIS]?.split('\n')?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

    suspend fun setWallpaperUris(uris: List<String>) {
        store.edit {
            it[Keys.WP_URIS] = uris.joinToString("\n")
            it[Keys.WP_INDEX] = 0
        }
    }

    val wallpaperIndex: Flow<Int> = store.data.map { it[Keys.WP_INDEX] ?: 0 }

    suspend fun setWallpaperIndex(index: Int) {
        store.edit { it[Keys.WP_INDEX] = index }
    }

    // ---- Widgets ----

    val widgets: Flow<List<WidgetEntry>> = store.data.map {
        it[Keys.WIDGETS]?.split('\n')
            ?.mapNotNull { s -> WidgetEntry.deserialize(s) } ?: emptyList()
    }

    suspend fun setWidgets(entries: List<WidgetEntry>) {
        store.edit { it[Keys.WIDGETS] = entries.joinToString("\n") { e -> e.serialize() } }
    }

    // ---- Custom screens (extra widget spaces) ----

    /** User-created pager pages, in order. Ids start at 1; 0 = built-in Widgets. */
    val customScreens: Flow<List<CustomScreen>> = store.data.map {
        it[Keys.CUSTOM_SCREENS]?.split('\n')
            ?.mapNotNull { s -> CustomScreen.deserialize(s) } ?: emptyList()
    }

    suspend fun setCustomScreens(screens: List<CustomScreen>) {
        store.edit {
            it[Keys.CUSTOM_SCREENS] = screens.joinToString("\n") { s -> s.serialize() }
        }
    }

    // ---- Custom categories & per-app category overrides ----

    /** User-created categories, in user order. */
    val customCategories: Flow<List<UserCategory>> = store.data.map {
        it[Keys.CUSTOM_CATEGORIES]?.split('\n')
            ?.mapNotNull(UserCategory::deserialize) ?: emptyList()
    }

    suspend fun setCustomCategories(categories: List<UserCategory>) {
        store.edit {
            it[Keys.CUSTOM_CATEGORIES] =
                categories.joinToString("\n") { c -> c.serialize() }
        }
    }

    /** Appends a new category; returns its id (max+1, starting at 1). */
    suspend fun addCustomCategory(name: String, glyph: CategoryGlyph): Int {
        var newId = 1
        store.edit { p ->
            val current = p[Keys.CUSTOM_CATEGORIES]?.split('\n')
                ?.mapNotNull(UserCategory::deserialize) ?: emptyList()
            newId = (current.maxOfOrNull { it.id } ?: 0) + 1
            p[Keys.CUSTOM_CATEGORIES] = (current + UserCategory(newId, name, glyph))
                .joinToString("\n") { c -> c.serialize() }
        }
        return newId
    }

    suspend fun updateCustomCategory(category: UserCategory) {
        store.edit { p ->
            val current = p[Keys.CUSTOM_CATEGORIES]?.split('\n')
                ?.mapNotNull(UserCategory::deserialize) ?: emptyList()
            p[Keys.CUSTOM_CATEGORIES] = current
                .map { if (it.id == category.id) category else it }
                .joinToString("\n") { c -> c.serialize() }
        }
    }

    /** Removes the category and every override/order pointing to it. */
    suspend fun deleteCustomCategory(id: Int) {
        val key = "custom:$id"
        store.edit { p ->
            val current = p[Keys.CUSTOM_CATEGORIES]?.split('\n')
                ?.mapNotNull(UserCategory::deserialize) ?: emptyList()
            p[Keys.CUSTOM_CATEGORIES] = current
                .filter { it.id != id }
                .joinToString("\n") { c -> c.serialize() }
            p[Keys.CATEGORY_OVERRIDES] = serializeOverrides(
                parseOverrides(p[Keys.CATEGORY_OVERRIDES])
                    .filterValues { it != key }
            )
            p[Keys.APP_ORDER] = serializeAppOrder(
                parseAppOrder(p[Keys.APP_ORDER]) - key
            )
            p[Keys.CATEGORY_ORDER] = (
                p[Keys.CATEGORY_ORDER]?.split('\n') ?: emptyList()
                ).filter { it != key }.joinToString("\n")
        }
    }

    /** componentKey -> categoryKey ("PRODUCTIVITY", "custom:3", …). */
    val categoryOverrides: Flow<Map<String, String>> = store.data.map {
        parseOverrides(it[Keys.CATEGORY_OVERRIDES])
    }

    suspend fun setCategoryOverrides(overrides: Map<String, String>) {
        store.edit {
            it[Keys.CATEGORY_OVERRIDES] = serializeOverrides(overrides)
        }
    }

    /** One app: set a category, or pass null to return to automatic. */
    suspend fun setOverride(componentKey: String, categoryKey: String?) {
        store.edit { p ->
            val map = parseOverrides(p[Keys.CATEGORY_OVERRIDES]).toMutableMap()
            if (categoryKey == null) map.remove(componentKey)
            else map[componentKey] = categoryKey
            p[Keys.CATEGORY_OVERRIDES] = serializeOverrides(map)
        }
    }

    /** Many apps in one write (e.g. the add-apps checklist). */
    suspend fun setOverrides(componentKeys: Collection<String>, categoryKey: String?) {
        store.edit { p ->
            val map = parseOverrides(p[Keys.CATEGORY_OVERRIDES]).toMutableMap()
            componentKeys.forEach { key ->
                if (categoryKey == null) map.remove(key) else map[key] = categoryKey
            }
            p[Keys.CATEGORY_OVERRIDES] = serializeOverrides(map)
        }
    }

    // ---- Drag-order persistence ----

    /** Card order on the Collections page; unlisted keys keep default order. */
    val categoryOrder: Flow<List<String>> = store.data.map {
        it[Keys.CATEGORY_ORDER]?.split('\n')?.filter { s -> s.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setCategoryOrder(keys: List<String>) {
        store.edit { it[Keys.CATEGORY_ORDER] = keys.joinToString("\n") }
    }

    /** Per-category pinned app order (componentKeys in drag order). */
    val appOrder: Flow<Map<String, List<String>>> = store.data.map {
        parseAppOrder(it[Keys.APP_ORDER])
    }

    /**
     * One-edit drop commit: points [componentKey]'s override at [targetKey]
     * (or clears it when dropping back onto the automatic [autoKey]) and
     * records the resulting pinned order for that card.
     */
    suspend fun applyAppDrop(
        componentKey: String,
        autoKey: String,
        targetKey: String,
        targetOrder: List<String>,
    ) {
        store.edit { p ->
            val map = parseOverrides(p[Keys.CATEGORY_OVERRIDES]).toMutableMap()
            if (targetKey == autoKey) map.remove(componentKey)
            else map[componentKey] = targetKey
            p[Keys.CATEGORY_OVERRIDES] = serializeOverrides(map)
            val orders = parseAppOrder(p[Keys.APP_ORDER]).toMutableMap()
            orders[targetKey] = targetOrder
            p[Keys.APP_ORDER] = serializeAppOrder(orders)
        }
    }

    // ---- Default Home widgets ----

    /** Set after the first-run seed of Google's default Home widgets. */
    val homeWidgetsSeeded: Flow<Boolean> = store.data.map {
        it[Keys.HOME_WIDGETS_SEEDED] ?: false
    }

    suspend fun setHomeWidgetsSeeded(seeded: Boolean) {
        store.edit { it[Keys.HOME_WIDGETS_SEEDED] = seeded }
    }

    companion object {
        const val DEFAULT_GRID_SIZE = 5

        // componentKeys contain '/' and '.' but never '='; split on the last
        // '=' so either side may carry extra separators.
        private fun parseOverrides(raw: String?): Map<String, String> =
            raw?.split('\n')
                ?.mapNotNull { line ->
                    val i = line.lastIndexOf('=')
                    if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
                }
                ?.toMap() ?: emptyMap()

        private fun serializeOverrides(map: Map<String, String>): String =
            map.entries.joinToString("\n") { (k, v) -> "$k=$v" }

        // "categoryKey=ck1|ck2|..." per line; neither '=' nor '|' appears in keys.
        private fun parseAppOrder(raw: String?): Map<String, List<String>> =
            raw?.split('\n')
                ?.mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null
                    else line.substring(0, i) to
                        line.substring(i + 1).split('|').filter { it.isNotBlank() }
                }
                ?.toMap() ?: emptyMap()

        private fun serializeAppOrder(map: Map<String, List<String>>): String =
            map.entries.joinToString("\n") { (k, v) -> "$k=${v.joinToString("|")}" }

        private const val OVERRIDE_ORIGINAL = "@original"

        // "componentKey=packPackage|drawable" or "componentKey=@original";
        // componentKeys never contain '=' so split on the first.
        internal fun parseIconOverrides(raw: String?): Map<String, IconOverride> =
            raw?.split('\n')
                ?.mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) return@mapNotNull null
                    val key = line.substring(0, i)
                    val value = line.substring(i + 1)
                    val override = when {
                        value == OVERRIDE_ORIGINAL -> IconOverride.Original
                        else -> {
                            val sep = value.indexOf('|')
                            if (sep <= 0 || sep == value.length - 1) {
                                null
                            } else {
                                IconOverride.Pack(
                                    value.substring(0, sep),
                                    value.substring(sep + 1),
                                )
                            }
                        }
                    }
                    override?.let { key to it }
                }
                ?.toMap() ?: emptyMap()

        internal fun serializeIconOverrides(map: Map<String, IconOverride>): String =
            map.entries.joinToString("\n") { (key, override) ->
                when (override) {
                    is IconOverride.Original -> "$key=$OVERRIDE_ORIGINAL"
                    is IconOverride.Pack -> "$key=${override.packPackage}|${override.drawable}"
                }
            }
    }
}
