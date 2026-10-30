package com.vibiate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shop
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Resolves the 10 dock slots (2 rows x 5) off the main thread.
 *
 * Each slot tries, in order: a preferred package's launch intent, then a list of
 * role/category intents, then package-name hints over launchable apps. Anything
 * still unresolved is filled by the next installed launchable app not already in
 * the dock; the last resort is a glyph-only placeholder with a no-op intent.
 */
class DockRepository(private val context: Context, private val settings: SettingsRepository) {

    private data class SlotSpec(
        val glyph: ImageVector,
        val preferredPackage: String? = null,
        val intents: List<Intent> = emptyList(),
        val packageHints: List<String> = emptyList(),
    )

    private fun appCategory(category: String): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(category)

    private fun launcherIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    // Row 1: calls, calendar, messages, mail, maps.
    private val row1Specs = listOf(
        SlotSpec(
            glyph = Icons.Filled.Call,
            intents = listOf(Intent(Intent.ACTION_DIAL)),
        ),
        SlotSpec(
            glyph = Icons.Filled.DateRange,
            intents = listOf(appCategory(Intent.CATEGORY_APP_CALENDAR)),
            packageHints = listOf("calendar"),
        ),
        SlotSpec(
            glyph = Icons.AutoMirrored.Filled.Message,
            intents = listOf(appCategory(Intent.CATEGORY_APP_MESSAGING)),
            packageHints = listOf("messaging", "mms", "sms"),
        ),
        SlotSpec(
            glyph = Icons.Filled.Email,
            preferredPackage = "com.google.android.gm",
            intents = listOf(appCategory(Intent.CATEGORY_APP_EMAIL)),
            packageHints = listOf("mail", "email"),
        ),
        SlotSpec(
            glyph = Icons.Filled.Place,
            preferredPackage = "com.google.android.apps.maps",
            intents = listOf(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))),
            packageHints = listOf("maps"),
        ),
    )

    // Row 2: browser, camera, gallery, Play Store, files.
    private val row2Specs = listOf(
        SlotSpec(
            glyph = Icons.Filled.Public,
            intents = listOf(
                appCategory(Intent.CATEGORY_APP_BROWSER),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            ),
            packageHints = listOf("browser", "chrome"),
        ),
        SlotSpec(
            glyph = Icons.Filled.PhotoCamera,
            intents = listOf(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            ),
            packageHints = listOf("camera"),
        ),
        SlotSpec(
            glyph = Icons.Filled.Photo,
            intents = listOf(
                appCategory(Intent.CATEGORY_APP_GALLERY),
                Intent(Intent.ACTION_VIEW).setType("image/*"),
            ),
            packageHints = listOf("gallery", "photos"),
        ),
        SlotSpec(
            glyph = Icons.Filled.Shop,
            preferredPackage = "com.android.vending",
            intents = listOf(
                appCategory(Intent.CATEGORY_APP_MARKET),
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending")),
            ),
        ),
        SlotSpec(
            glyph = Icons.Filled.Folder,
            intents = listOf(appCategory(Intent.CATEGORY_APP_FILES)),
            packageHints = listOf("files", "filemanager", "documentsui"),
        ),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _entries = MutableStateFlow(
        (row1Specs + row2Specs).map { placeholder(it) as DockEntry? }
    )
    /** Exactly [DOCK_MAX] slots; a null element is an empty spot. */
    val entries: StateFlow<List<DockEntry?>> = _entries

    @Volatile
    private var resolved = false

    /** Last stored slot list; null means "never customised" -> auto-resolution. */
    @Volatile
    private var storedKeys: List<String?>? = null

    init {
        scope.launch {
            settings.dockApps.collect { stored ->
                storedKeys = stored
                _entries.value = if (stored == null) resolveDock() else resolveStored(stored)
            }
        }
    }

    /** Resolve once lazily; safe to call repeatedly. */
    fun ensureLoaded() {
        if (!resolved) invalidate()
    }

    fun invalidate() {
        scope.launch {
            _entries.value = storedKeys?.let(::resolveStored) ?: resolveDock()
        }
    }

    /**
     * Component keys ("pkg/activity") of the currently shown real entries.
     * Each auto-resolved component is mapped to its package's launcher
     * activity so the keys match the A–Z componentKey format (e.g. the Phone
     * slot resolved via ACTION_DIAL becomes the dialer's launch activity).
     */
    fun currentKeys(): List<String?> {
        val pm = context.packageManager
        return _entries.value.map { e ->
            e?.component?.let { cn ->
                dockKey(
                    runCatching {
                        pm.getLaunchIntentForPackage(cn.packageName)?.component
                    }.getOrNull() ?: cn
                )
            }
        }
    }

    /**
     * Apply a dock edit. The first edit materialises the currently shown real
     * components (glyph placeholders dropped) before the transform runs.
     */
    fun edit(transform: (List<String?>) -> List<String?>) {
        scope.launch {
            settings.setDockApps(transform(storedKeys ?: currentKeys()))
        }
    }

    /** Clear the persisted dock and fall back to automatic resolution. */
    fun reset() {
        scope.launch { settings.clearDockApps() }
    }

    /**
     * Rebuild entries from persisted slot keys. If the stored activity isn't
     * one of the package's launcher activities, the package's own launch
     * intent component is used instead; an entry becomes a hole only when
     * the package itself is gone � positions never compact.
     */
    private fun resolveStored(keys: List<String?>): List<DockEntry?> {
        val pm = context.packageManager
        val resolved = mutableSetOf<String>()
        return List(DOCK_MAX) { i ->
            val key = keys.getOrNull(i) ?: return@List null
            val cn = runCatching { ComponentName.unflattenFromString(key) }.getOrNull()
                ?: return@List null
            val ris = runCatching {
                pm.queryIntentActivities(
                    launcherIntent().apply { setPackage(cn.packageName) },
                    PackageManager.MATCH_ALL,
                )
            }.getOrDefault(emptyList())
            val ri = ris.firstOrNull { it.activityInfo.name == cn.className }
                ?: runCatching { pm.getLaunchIntentForPackage(cn.packageName) }
                    .getOrNull()?.component?.let { target ->
                        ris.firstOrNull {
                            it.activityInfo.name == target.className
                        }
                    }
                ?: return@List null
            val resolvedCn = ComponentName(
                ri.activityInfo.packageName, ri.activityInfo.name
            )
            if (!resolved.add(dockKey(resolvedCn))) return@List null
            DockEntry(
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm),
                glyph = Icons.Filled.Folder,
                intent = launcherIntent().apply {
                    component = resolvedCn
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                component = resolvedCn,
            )
        }
    }

    private fun placeholder(spec: SlotSpec) = DockEntry(
        label = "",
        icon = null,
        glyph = spec.glyph,
        intent = Intent(),
        component = null,
    )

    private fun resolveDock(): List<DockEntry?> {
        val pm = context.packageManager
        val specs = row1Specs + row2Specs
        val usedPackages = mutableSetOf(context.packageName)

        val launchables = runCatching {
            pm.queryIntentActivities(launcherIntent(), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        fun toEntry(ri: ResolveInfo, base: Intent, glyph: ImageVector): DockEntry {
            val component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
            val intent = Intent(base).apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            usedPackages += component.packageName
            return DockEntry(
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm),
                glyph = glyph,
                intent = intent,
                component = component,
            )
        }

        fun resolve(spec: SlotSpec): DockEntry? {
            spec.preferredPackage?.let { pkg ->
                val launch = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
                val ri = launch?.let {
                    runCatching { pm.resolveActivity(it, PackageManager.MATCH_ALL) }.getOrNull()
                }
                if (ri != null && ri.activityInfo.packageName !in usedPackages) {
                    return toEntry(ri, launch, spec.glyph)
                }
            }
            for (intent in spec.intents) {
                val ri = runCatching {
                    pm.resolveActivity(intent, PackageManager.MATCH_ALL)
                }.getOrNull()
                if (ri != null && ri.activityInfo.packageName !in usedPackages) {
                    return toEntry(ri, intent, spec.glyph)
                }
            }
            for (hint in spec.packageHints) {
                val ri = launchables.firstOrNull {
                    it.activityInfo.packageName.contains(hint, ignoreCase = true) &&
                        it.activityInfo.packageName !in usedPackages
                }
                if (ri != null) return toEntry(ri, launcherIntent(), spec.glyph)
            }
            return null
        }

        val results = specs.map { spec -> runCatching { resolve(spec) }.getOrNull() }

        // Fill unresolved slots with the next launchable app not already docked.
        val fillers = launchables
            .filter { it.activityInfo.packageName !in usedPackages }
            .iterator()

        return specs.mapIndexed { index, spec ->
            results[index]
                ?: if (fillers.hasNext()) toEntry(fillers.next(), launcherIntent(), spec.glyph)
                else placeholder(spec)
        }.also { resolved = true }
    }
}
