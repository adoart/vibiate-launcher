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
import com.vibiate.launcher.data.PageShortcut
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

    private val _shortcuts = MutableStateFlow<List<PageShortcut>>(emptyList())
    val shortcuts: StateFlow<List<PageShortcut>> = _shortcuts

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
        _shortcuts.value = _shortcuts.value.filterNot { it.screenId == screenId }
        persist()
        persistShortcuts()
        scope.launch {
            val screens = settings.customScreens.first()
            settings.setCustomScreens(screens.filterNot { it.id == screenId })
        }
    }

    fun createView(entry: WidgetEntry): AppWidgetHostView? {
        val info = providerInfo(entry.appWidgetId) ?: return null
        return host.createView(context, entry.appWidgetId, info)
    }

    /**
     * Persist fixed grid positions for [screenId]'s items; [spans] also
     * freezes explicit widget spans. Negative position keys (-shortcut.id)
     * update page shortcuts pushed around by widget moves/resizes.
     */
    fun placeWidgets(
        screenId: Int,
        positions: Map<Int, GridPos>,
        spans: Map<Int, GridSpan> = emptyMap(),
    ) {
        _widgets.value = _widgets.value.map {
            if (it.screenId != screenId) {
                it
            } else {
                val pos = positions[it.appWidgetId]
                val span = spans[it.appWidgetId]
                it.copy(
                    col = pos?.col ?: it.col,
                    row = pos?.row ?: it.row,
                    cols = span?.cols ?: it.cols,
                    rows = span?.rows ?: it.rows,
                )
            }
        }
        _shortcuts.value = _shortcuts.value.map {
            if (it.screenId != screenId) {
                it
            } else {
                val pos = positions[shortcutGridId(it.id)]
                it.copy(col = pos?.col ?: it.col, row = pos?.row ?: it.row)
            }
        }
        persist()
        persistShortcuts()
    }

    /**
     * Land a shortcut for [componentKey] at the position [positions] gives it
     * on [screenId] (keys are grid ids: appWidgetId, -shortcut.id — the same
     * map the drop preview produced). The same app already on this screen
     * moves instead of duplicating; [movingShortcutId] carries a dragged
     * shortcut onto this screen; widget positions in the map shift too.
     */
    fun dropShortcut(
        screenId: Int,
        componentKey: String,
        positions: Map<Int, GridPos>,
        movingShortcutId: Int? = null,
    ) {
        val id = shortcutDropId(
            sameComponentId = _shortcuts.value.firstOrNull {
                it.screenId == screenId && it.componentKey == componentKey
            }?.id,
            movingShortcutId = movingShortcutId,
            nextId = (_shortcuts.value.maxOfOrNull { it.id } ?: 0) + 1,
        )
        val targetPos = positions[shortcutGridId(id)] ?: return
        val existing = _shortcuts.value.firstOrNull { it.id == id }
        // A dragged shortcut merging into the same app on this page is moved,
        // not copied: drop the source.
        val list = _shortcuts.value
            .filterNot { movingShortcutId != null && movingShortcutId != id && it.id == movingShortcutId }
            .toMutableList()
        if (existing != null) {
            list[list.indexOf(existing)] = existing.copy(
                screenId = screenId,
                col = targetPos.col,
                row = targetPos.row,
            )
        } else {
            list += PageShortcut(id, componentKey, screenId, targetPos.col, targetPos.row)
        }
        _shortcuts.value = list
        // Persist widget positions + all other shortcut positions for the
        // page through the shared path.
        placeWidgets(screenId, positions)
        persistShortcuts()
    }

    fun removeShortcut(id: Int) {
        _shortcuts.value = _shortcuts.value.filterNot { it.id == id }
        persistShortcuts()
    }

    /** True once the persisted widget list has been restored at startup. */
    val restoredFlow = MutableStateFlow(false)

    private suspend fun restore() {
        val saved = settings.widgets.first()
        // Drop entries whose ids are no longer bound (e.g. provider uninstalled).
        val valid = saved.filter { providerInfo(it.appWidgetId) != null }
        _widgets.value = valid
        _shortcuts.value = settings.shortcuts.first()
        if (valid.size != saved.size) settings.setWidgets(valid)
        restoredFlow.value = true
    }

    private fun persist() {
        val snapshot = _widgets.value
        scope.launch { settings.setWidgets(snapshot) }
    }

    private fun persistShortcuts() {
        val snapshot = _shortcuts.value
        scope.launch { settings.setShortcuts(snapshot) }
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
    var onDragStart: ((downRawX: Float, downRawY: Float) -> Unit)? = null
    var onDragMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onDragEnd: ((cancelled: Boolean) -> Unit)? = null

    /**
     * Edit mode: this widget is selected for editing. The view intercepts
     * every gesture from ACTION_DOWN (the widget's own clicks never fire)
     * and a body drag starts immediately past touch slop — no long-press.
     */
    var editMode = false

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
                // In edit mode the parent (pager/scroll) must never grab a
                // move that could start a body drag.
                if (editMode) parent?.requestDisallowInterceptTouchEvent(true)
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
        // dispatches ACTION_CANCEL to the child that had it. In edit mode we
        // take every gesture so widget clicks can't fire.
        return longPressFired || editMode || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (longPressFired || editMode) {
            // Movement past slop turns the gesture into a widget drag that
            // Compose mirrors in raw coords — after a long-press anywhere,
            // or immediately in edit mode.
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (!dragActive &&
                        (abs(event.rawX - downX) > slop ||
                            abs(event.rawY - downY) > slop)
                    ) {
                        dragActive = true
                        removeCallbacks(longPressCheck)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onDragStart?.invoke(downX, downY)
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
