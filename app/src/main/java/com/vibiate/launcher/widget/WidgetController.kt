package com.vibiate.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import com.vibiate.launcher.data.CustomScreen
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.WidgetEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the [AppWidgetHost] (hostId 1024) and the persisted list of bound widgets.
 * startListening/stopListening are tied to the activity lifecycle by the caller.
 */
class WidgetController(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    val host = AppWidgetHost(context, HOST_ID)
    private val manager = AppWidgetManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _widgets = MutableStateFlow<List<WidgetEntry>>(emptyList())
    val widgets: StateFlow<List<WidgetEntry>> = _widgets

    // restore() rewrites _widgets from DataStore asynchronously; running it on
    // every ON_START lets a stale read clobber a widget added while an opaque
    // configure activity was in front. Restore once per controller lifetime.
    private val restored = AtomicBoolean(false)

    fun startListening() {
        host.startListening()
        if (restored.compareAndSet(false, true)) {
            scope.launch { restore() }
        }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    /** Release an allocated-but-unbound widget id (canceled pick/bind). */
    fun discardId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(appWidgetId, provider) }
            .getOrDefault(false)

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /**
     * Record a bound widget. [screenId] is 0 for the built-in Widgets page or a
     * [CustomScreen] id for a user-created space.
     */
    fun addWidget(appWidgetId: Int, provider: ComponentName, screenId: Int = 0) {
        val entry = WidgetEntry(appWidgetId, provider.packageName, provider.className, screenId)
        _widgets.value = _widgets.value.filterNot { it.appWidgetId == appWidgetId } + entry
        persist()
    }

    fun removeWidget(appWidgetId: Int) {
        discardId(appWidgetId)
        _widgets.value = _widgets.value.filterNot { it.appWidgetId == appWidgetId }
        persist()
    }

    // ---- Custom screens ----

    /**
     * Append a new user screen. [nameFor] produces the display name for the
     * assigned id (localized by the caller, e.g. "Space 2").
     */
    fun addScreen(nameFor: (Int) -> String) {
        scope.launch {
            val screens = settings.customScreens.first()
            val nextId = (screens.maxOfOrNull { it.id } ?: 0) + 1
            settings.setCustomScreens(screens + CustomScreen(nextId, nameFor(nextId)))
        }
    }

    /** Delete a custom screen: release its widget ids and drop it from settings. */
    fun removeScreen(screenId: Int) {
        if (screenId <= 0) return // screen 0 is the built-in Widgets page
        _widgets.value
            .filter { it.screenId == screenId }
            .forEach { discardId(it.appWidgetId) }
        _widgets.value = _widgets.value.filterNot { it.screenId == screenId }
        persist()
        scope.launch {
            val screens = settings.customScreens.first()
            settings.setCustomScreens(screens.filterNot { it.id == screenId })
        }
    }

    fun createView(entry: WidgetEntry): AppWidgetHostView? {
        val info = providerInfo(entry.appWidgetId) ?: return null
        return host.createView(context, entry.appWidgetId, info)
    }

    private suspend fun restore() {
        val saved = settings.widgets.first()
        // Drop entries whose ids are no longer bound (e.g. provider uninstalled).
        val valid = saved.filter { providerInfo(it.appWidgetId) != null }
        _widgets.value = valid
        if (valid.size != saved.size) settings.setWidgets(valid)
    }

    private fun persist() {
        val snapshot = _widgets.value
        scope.launch { settings.setWidgets(snapshot) }
    }

    companion object {
        const val HOST_ID = 1024
    }
}
