package com.vibiate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Parsed appfilter.xml contents, kept Android-free so unit tests can build it
 * directly through [AppFilterBuilder].
 */
class AppFilter(
    /** Exact "pkg/cls" component key -> drawable resource name. */
    val components: Map<String, String>,
    /** "pkg" -> first drawable seen for that package (package-level fallback). */
    val packages: Map<String, String>,
    /** Exact component key -> calendar icon prefix. */
    val calendars: Map<String, String>,
    /** "pkg" -> calendar prefix (first seen). */
    val calendarPackages: Map<String, String>,
    /** All imgN attributes of <iconback>, ordered by N. */
    val iconBacks: List<String>,
    /** img1 of <iconmask>. */
    val iconMask: String?,
    /** img1 of <iconupon>. */
    val iconUpon: String?,
    /** <scale factor>, clamped to 0.1..2; 1f when absent. */
    val scale: Float,
) {
    /** True when the pack can re-mask unthemed icons. */
    val hasMask: Boolean
        get() = iconBacks.isNotEmpty() || iconMask != null ||
            iconUpon != null || scale != 1f
}

/** Accumulates appfilter tags; [build] freezes the result. First entry wins. */
class AppFilterBuilder {

    private val components = LinkedHashMap<String, String>()
    private val packages = HashMap<String, String>()
    private val calendars = LinkedHashMap<String, String>()
    private val calendarPackages = HashMap<String, String>()
    private val iconBacks = sortedMapOf<Int, String>()
    private var iconMask: String? = null
    private var iconUpon: String? = null
    private var scale = 1f

    fun onTag(name: String, attrs: Map<String, String>) {
        when (name) {
            "item" -> {
                val raw = attrs["component"] ?: return
                val drawable = attrs["drawable"] ?: return
                val key = componentToKey(raw) ?: return
                if (key !in components) {
                    components[key] = drawable
                    packages.putIfAbsent(key.substringBefore('/'), drawable)
                }
            }
            "calendar" -> {
                val raw = attrs["component"] ?: return
                val prefix = attrs["prefix"] ?: return
                val key = componentToKey(raw) ?: return
                if (key !in calendars) {
                    calendars[key] = prefix
                    calendarPackages.putIfAbsent(key.substringBefore('/'), prefix)
                }
            }
            "iconback" -> attrs.forEach { (attr, value) ->
                val n = attr.removePrefix("img").toIntOrNull()
                if (n != null && attr.startsWith("img") && value.isNotEmpty()) {
                    iconBacks[n] = value
                }
            }
            "iconmask" -> {
                attrs["img1"]?.takeIf { it.isNotEmpty() }?.let { iconMask = it }
            }
            "iconupon" -> {
                attrs["img1"]?.takeIf { it.isNotEmpty() }?.let { iconUpon = it }
            }
            "scale" -> attrs["factor"]?.toFloatOrNull()?.let {
                scale = it.coerceIn(0.1f, 2f)
            }
        }
    }

    fun build(): AppFilter = AppFilter(
        components = components,
        packages = packages,
        calendars = calendars,
        calendarPackages = calendarPackages,
        iconBacks = iconBacks.values.toList(),
        iconMask = iconMask,
        iconUpon = iconUpon,
        scale = scale,
    )

    companion object {
        /** "ComponentInfo{pkg/cls}" -> "pkg/cls"; also accepts raw "pkg/cls". */
        fun componentToKey(raw: String): String? {
            var s = raw.trim()
            if (s.startsWith("ComponentInfo{") && s.endsWith("}")) {
                s = s.substringAfter('{').substringBefore('}')
            }
            if (!s.contains('/')) return null
            val pkg = s.substringBefore('/')
            var cls = s.substringAfter('/')
            if (cls.startsWith(".")) cls = pkg + cls
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return "$pkg/$cls"
        }
    }
}

/**
 * One installed icon pack: its resources plus the parsed [AppFilter].
 * [loadDrawable] and [themedDrawable] must be called off the main thread.
 */
class IconPack(
    val packageName: String,
    val label: String,
    private val resources: Resources,
    val filter: AppFilter,
) {
    private val idCache = HashMap<String, Int>()

    /** Resolves a drawable name to a Drawable; misses are cached as 0. */
    @Synchronized
    private fun drawableId(name: String): Int =
        idCache.getOrPut(name) {
            var id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) resources.getIdentifier(name, "mipmap", packageName) else id
        }

    fun loadDrawable(name: String): Drawable? {
        val id = drawableId(name)
        if (id == 0) return null
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }

    /**
     * Drawable name for [component]: exact-component calendar prefix
     * (prefix + day-of-month) first so date icons win over a static <item>
     * mapping, then the exact component, then the package-level calendar
     * prefix, then the package-level fallback.
     */
    fun themedName(component: ComponentName, dayOfMonth: Int): String? {
        val key = "${component.packageName}/${component.className}"
        filter.calendars[key]?.let { prefix ->
            if (drawableId(prefix + dayOfMonth) != 0) return prefix + dayOfMonth
        }
        filter.components[key]?.let { return it }
        filter.calendarPackages[component.packageName]?.let { prefix ->
            if (drawableId(prefix + dayOfMonth) != 0) return prefix + dayOfMonth
        }
        return filter.packages[component.packageName]
    }

    fun themedDrawable(component: ComponentName, dayOfMonth: Int): Drawable? =
        themedName(component, dayOfMonth)?.let { loadDrawable(it) }

    val hasMask: Boolean get() = filter.hasMask

    /**
     * Icon names for the picker: res/xml/drawable.xml (fallback
     * assets/drawable.xml) <item drawable=…> in order, de-duplicated and
     * limited to names that resolve; falls back to the appfilter's drawables.
     */
    val iconNames: List<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val names = linkedSetOf<String>()
        val resId = resources.getIdentifier("drawable", "xml", packageName)
        if (resId != 0) {
            runCatching {
                resources.getXml(resId).use { parser ->
                    collectDrawableNames(parser, names)
                }
            }
        }
        if (names.isEmpty()) {
            runCatching {
                resources.assets.open("drawable.xml").use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, null)
                    collectDrawableNames(parser, names)
                }
            }
        }
        if (names.isNotEmpty()) {
            names.filter { drawableId(it) != 0 }
        } else {
            filter.components.values.distinct().sorted()
        }
    }

    private fun collectDrawableNames(parser: XmlPullParser, out: MutableSet<String>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "drawable") {
                        parser.getAttributeValue(i)?.takeIf { it.isNotEmpty() }
                            ?.let(out::add)
                    }
                }
            }
            event = parser.next()
        }
    }

    private inline fun XmlResourceParser.use(block: (XmlResourceParser) -> Unit) {
        try { block(this) } finally { close() }
    }
}

/**
 * Discovers installed icon packs via the well-known theme-intent actions and
 * parses their appfilter.xml into an [IconPack]. Packs are big (Arcticons:
 * ~50k items), so [loadPack] must run off the main thread; results are cached
 * and invalidated via [invalidate] on package changes.
 */
class IconPackManager(private val context: Context) {

    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.novalauncher.THEME",
        "com.fede.launcher.THEME",
        "com.lge.launcher2.THEME",
        "net.oneplus.launcher.icons.ACTION_PICK_ICON",
        "app.lawnchair.icons.THEMED_ICON",
        "com.dlto.atom.launcher.THEME",
        "ch.deletescape.lawnchair.THEME",
        "ch.deletescape.lawnchair.ICONPACK",
        "ginlemon.smartlauncher.THEMES",
        "com.sonymobile.home.ICON_PACK",
    )

    private val packs = ConcurrentHashMap<String, IconPack>()
    private val unparseable = ConcurrentHashMap.newKeySet<String>()
    private val parseLock = Any()

    /** Bumped on [invalidate] so UIs can rebuild the composed theme. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    /** Drop [pkg]'s cache entry (or all when null) and notify listeners. */
    fun invalidate(pkg: String?) {
        synchronized(parseLock) {
            if (pkg == null) {
                packs.clear()
                unparseable.clear()
            } else {
                packs.remove(pkg)
                unparseable.remove(pkg)
            }
        }
        _revision.value += 1
    }

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = linkedMapOf<String, String>()
        for (action in themeActions) {
            val intent = Intent(action)
            val matches = runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())
            for (ri in matches) {
                val pkg = ri.activityInfo.packageName
                if (pkg != context.packageName && !seen.containsKey(pkg)) {
                    seen[pkg] = ri.loadLabel(pm).toString()
                }
            }
        }
        return seen.map { (pkg, label) -> IconPackInfo(pkg, label) }
            .sortedBy { it.label.lowercase() }
    }

    /** Returns the parsed pack, or null if it can't be parsed. Cached. */
    fun loadPack(packageName: String): IconPack? {
        packs[packageName]?.let { return it }
        if (packageName in unparseable) return null
        synchronized(parseLock) {
            packs[packageName]?.let { return it }
            if (packageName in unparseable) return null
            val pack = runCatching { parsePack(packageName) }.getOrNull()
            if (pack == null) unparseable.add(packageName)
            else packs[packageName] = pack
            return pack
        }
    }

    private fun parsePack(packageName: String): IconPack? {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(packageName)
        val builder = AppFilterBuilder()

        // Primary: xml resource named appfilter; fallback: assets/appfilter.xml.
        val resId = res.getIdentifier("appfilter", "xml", packageName)
        if (resId != 0) {
            runCatching {
                res.getXml(resId).use { parser -> feed(parser, builder) }
            }
        }
        if (builder.build().components.isEmpty()) {
            runCatching {
                res.assets.open("appfilter.xml").use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, null)
                    feed(parser, builder)
                }
            }
        }
        val filter = builder.build()
        if (filter.components.isEmpty() && filter.calendars.isEmpty()) return null
        val label = pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        return IconPack(packageName, label, res, filter)
    }

    private fun feed(parser: XmlPullParser, builder: AppFilterBuilder) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val attrs = HashMap<String, String>(parser.attributeCount)
                for (i in 0 until parser.attributeCount) {
                    attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                }
                builder.onTag(parser.name, attrs)
            }
            event = parser.next()
        }
    }

    private inline fun XmlResourceParser.use(block: (XmlResourceParser) -> Unit) {
        try { block(this) } finally { close() }
    }
}
