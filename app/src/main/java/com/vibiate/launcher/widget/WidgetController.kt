package com.vibiate.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
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
    val host = LauncherAppWidgetHost(context, HOST_ID)
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
    fun addWidget(
        appWidgetId: Int,
        provider: ComponentName,
        screenId: Int = 0,
        cols: Int = 0,
        rows: Int = 0,
    ) {
        val entry = WidgetEntry(
            appWidgetId, provider.packageName, provider.className,
            screenId, cols, rows,
        )
        _widgets.value = _widgets.value.filterNot { it.appWidgetId == appWidgetId } + entry
        persist()
    }

    fun removeWidget(appWidgetId: Int) {
        discardId(appWidgetId)
        _widgets.value = _widgets.value.filterNot { it.appWidgetId == appWidgetId }
        persist()
    }

    /** Set a widget's grid span (cells). 0 on either axis returns to auto. */
    fun resizeWidget(appWidgetId: Int, cols: Int, rows: Int) {
        _widgets.value = _widgets.value.map {
            if (it.appWidgetId == appWidgetId) it.copy(cols = cols, rows = rows) else it
        }
        persist()
    }

    /** Set the small title shown above a widget; empty clears it. */
    fun renameWidget(appWidgetId: Int, title: String) {
        val t = title.trim().replace('\n', ' ').take(40)
        _widgets.value = _widgets.value.map {
            if (it.appWidgetId == appWidgetId) it.copy(title = t) else it
        }
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

    /** Reorder [screenId]'s widgets in place within the global list. */
    fun moveWidgets(screenId: Int, orderedIds: List<Int>) {
        _widgets.value = mergeScreenOrder(_widgets.value, screenId, orderedIds)
        persist()
    }

    /** True once the persisted widget list has been restored at startup. */
    val restoredFlow = MutableStateFlow(false)

    private suspend fun restore() {
        val saved = settings.widgets.first()
        // Drop entries whose ids are no longer bound (e.g. provider uninstalled).
        val valid = saved.filter { providerInfo(it.appWidgetId) != null }
        _widgets.value = valid
        if (valid.size != saved.size) settings.setWidgets(valid)
        restoredFlow.value = true
    }

    private fun persist() {
        val snapshot = _widgets.value
        scope.launch { settings.setWidgets(snapshot) }
    }

    companion object {
        const val HOST_ID = 1024
    }
}

/**
 * Host that creates [LongPressAppWidgetHostView]s so widget long-presses can
 * surface a context menu (AppWidgetHostView swallows touches, so Compose
 * never sees the gesture).
 */
class LauncherAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {

    /** Invoked with the host view whose widget was long-pressed. */
    var onWidgetLongPress: ((AppWidgetHostView) -> Unit)? = null

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        provider: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LongPressAppWidgetHostView(context).apply {
        onLongPress = { onWidgetLongPress?.invoke(this) }
    }
}

/**
 * [AppWidgetHostView] that detects long-press itself: on ACTION_DOWN it posts
 * a delayed check; moving beyond the touch slop or releasing cancels it. When
 * it fires, the rest of the gesture is intercepted so the widget doesn't also
 * receive a click.
 */
class LongPressAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onDragEnd: ((cancelled: Boolean) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private var dragActive = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressCheck = Runnable {
        longPressFired = true
        // Claim the gesture now: otherwise the page scroll / pager steal the
        // first move past slop and cancel us before a drag can start.
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                dragActive = false
                downX = ev.rawX
                downY = ev.rawY
                postDelayed(longPressCheck, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressFired &&
                    (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)
                ) {
                    removeCallbacks(longPressCheck)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                removeCallbacks(longPressCheck)
        }
        // Once the long-press fired, take over the gesture: framework
        // dispatches ACTION_CANCEL to the child that had it.
        return longPressFired || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (longPressFired) {
            // The long-press already fired; movement past slop turns the
            // gesture into a widget drag that Compose mirrors in raw coords.
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (!dragActive &&
                        (abs(event.rawX - downX) > slop ||
                            abs(event.rawY - downY) > slop)
                    ) {
                        dragActive = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onDragStart?.invoke()
                    }
                    if (dragActive) onDragMove?.invoke(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_UP -> {
                    if (dragActive) onDragEnd?.invoke(false)
                    reset()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragActive) onDragEnd?.invoke(true)
                    reset()
                }
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            removeCallbacks(longPressCheck)
        }
        // Claim DOWN even over non-clickable widget areas; otherwise the rest
        // of the gesture never reaches this view and a drag can't follow.
        return super.onTouchEvent(event) || event.actionMasked == MotionEvent.ACTION_DOWN
    }

    private fun reset() {
        longPressFired = false
        dragActive = false
        removeCallbacks(longPressCheck)
    }

    override fun onDetachedFromWindow() {
        // If the view ever detaches mid-drag, cancel rather than leaving the
        // launcher stuck in rearrange mode.
        if (dragActive) onDragEnd?.invoke(true)
        reset()
        super.onDetachedFromWindow()
    }
}
