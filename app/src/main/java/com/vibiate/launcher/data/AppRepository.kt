package com.vibiate.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loads launchable apps (labels + icons) off the main thread, classifies them into
 * [AppCategory] buckets, and caches the result. Refreshed via [invalidate] when
 * package broadcasts arrive.
 */
class AppRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadMutex = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Start loading if we haven't yet. Safe to call repeatedly. */
    fun ensureLoaded() {
        if (!_loaded.value) invalidate()
    }

    fun invalidate() {
        scope.launch { load() }
    }

    fun launchIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            setComponent(entry.component)
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private suspend fun load() {
        loadMutex.withLock {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            val entries = resolved
                .filter { it.activityInfo.packageName != context.packageName }
                .map { ri ->
                    val info = ri.activityInfo.applicationInfo
                    AppEntry(
                        packageName = ri.activityInfo.packageName,
                        activityName = ri.activityInfo.name,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm),
                        category = CategoryClassifier.classify(info),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            _apps.value = entries
            _loaded.value = true
        }
    }
}
