package com.vibiate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** A parsed icon pack: maps "pkg/activity" component keys to drawable resource names. */
class IconPackTheme(
    val packageName: String,
    private val resources: Resources,
    private val componentMap: Map<String, String>,
) {
    fun drawableFor(component: ComponentName): Drawable? {
        val key = "${component.packageName}/${component.className}"
        val name = componentMap[key] ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }
}

/**
 * Discovers installed icon packs via the well-known theme-intent actions and parses
 * their appfilter.xml into a component -> drawable map.
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
    )

    private val packCache = mutableMapOf<String, IconPackTheme?>()

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

    /** Returns the parsed pack, or null if it can't be parsed. Cached per package. */
    fun loadPack(packageName: String): IconPackTheme? {
        if (packCache.containsKey(packageName)) return packCache[packageName]
        val pack = runCatching { parsePack(packageName) }.getOrNull()
        packCache[packageName] = pack
        return pack
    }

    private fun parsePack(packageName: String): IconPackTheme? {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val map = mutableMapOf<String, String>()

        var parsed = false
        // Primary: xml resource named appfilter (then cheap fallbacks).
        for (resName in listOf("appfilter", "theme_config")) {
            val id = res.getIdentifier(resName, "xml", packageName)
            if (id != 0) {
                runCatching {
                    res.getXml(id).use { parser ->
                        parseAppfilter(parser, packageName, map)
                    }
                }
                if (map.isNotEmpty()) { parsed = true; break }
            }
        }
        // Fallback: assets/appfilter.xml (Atom-style packs).
        if (!parsed) {
            runCatching {
                res.assets.open("appfilter.xml").use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, null)
                    parseAppfilter(parser, packageName, map)
                }
            }
        }
        if (map.isEmpty()) return null
        return IconPackTheme(packageName, res, map)
    }

    private fun parseAppfilter(
        parser: XmlPullParser,
        packPackage: String,
        out: MutableMap<String, String>,
    ) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                var component: String? = null
                var drawable: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "component" -> component = parser.getAttributeValue(i)
                        "drawable" -> drawable = parser.getAttributeValue(i)
                    }
                }
                if (component != null && drawable != null) {
                    val key = componentToKey(component, packPackage)
                    if (key != null) out[key] = drawable
                }
            }
            event = parser.next()
        }
    }

    /** "ComponentInfo{pkg/cls}" -> "pkg/cls"; also accepts raw "pkg/cls". */
    private fun componentToKey(raw: String, packPackage: String): String? {
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

    private inline fun XmlResourceParser.use(block: (XmlResourceParser) -> Unit) {
        try { block(this) } finally { close() }
    }
}
