package com.vibiate.launcher.ui.drag

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.CollectionDef

/** What is being dragged: an app (from A–Z or a card) or a whole category. */
sealed interface DragPayload {
    data class App(val app: AppEntry, val sourceKey: String?) : DragPayload
    data class Category(val def: CollectionDef) : DragPayload
}

/** Grid slot drop target inside a card; [index] = row * gridSize + col. */
data class SlotId(val key: String, val index: Int)

/** Whole-card drop target (header + body). */
data class CardId(val key: String)

/** Shared drag session: payload, finger position, and registered drop rects. */
@Stable
class DragDropState {
    var payload: DragPayload? by mutableStateOf(null)
        private set
    var pointer: Offset by mutableStateOf(Offset.Zero)
        private set
    val isDragging get() = payload != null
    val rects = mutableStateMapOf<Any, Rect>()
    var dropHandler: ((DragPayload) -> Unit)? = null

    fun start(p: DragPayload, rootPos: Offset) {
        payload = p
        pointer = rootPos
    }

    fun moveBy(delta: Offset) {
        pointer += delta
    }

    fun drop() {
        payload?.let { dropHandler?.invoke(it) }
        payload = null
    }

    fun cancel() {
        payload = null
    }
}

val LocalDragDrop = staticCompositionLocalOf<DragDropState> {
    error("no DragDropState")
}

/** Registers this element's root-space bounds as a drop target under [id]. */
@Composable
fun Modifier.trackBounds(id: Any): Modifier {
    val state = LocalDragDrop.current
    DisposableEffect(id) {
        onDispose { state.rects.remove(id) }
    }
    return this.onGloballyPositioned { state.rects[id] = it.boundsInRoot() }
}

/**
 * Tap / long-press / drag in one gesture so they don't fight: a tap calls
 * [onTap]; a long-press starts a drag; if the finger never moves past slop
 * the drag is cancelled and [onLongPressNoMove] runs instead (menus).
 * [onDragMoved] fires once when movement first exceeds touch slop (e.g. the
 * A–Z source uses it to send the pager to Collections).
 */
@Composable
fun Modifier.launcherDragGestures(
    payload: () -> DragPayload?,
    onTap: () -> Unit,
    onLongPressNoMove: (() -> Unit)?,
    onDragMoved: (() -> Unit)? = null,
): Modifier {
    val state = LocalDragDrop.current
    val haptics = LocalHapticFeedback.current
    val payloadState = rememberUpdatedState(payload)
    val tapState = rememberUpdatedState(onTap)
    val longPressState = rememberUpdatedState(onLongPressNoMove)
    val movedState = rememberUpdatedState(onDragMoved)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Claim the pointer like combinedClickable does so parent
                // detectors (empty-space long-press, pager) skip this gesture.
                down.consume()
                val lp = awaitLongPressOrCancellation(down.id)
                if (lp == null) {
                    // Only a tap if our pointer went up unconsumed; anything
                    // else was a scroll/swipe a parent already handled.
                    if (currentEvent.changes.any {
                        it.id == down.id && it.changedToUp() && !it.isConsumed
                    }
                    ) {
                        tapState.value()
                    }
                } else {
                    // Empty slots report no payload: nothing to lift, so the
                    // touch falls back to plain scrolling behavior.
                    val p = payloadState.value() ?: return@awaitEachGesture
                    // Capture now: starting the drag makes the preview swap the
                    // pressed item for a placeholder, nulling this callback by
                    // the time a no-move release would invoke it.
                    val longPress = longPressState.value
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(p, coords?.localToRoot(lp.position) ?: lp.position)
                    var moved = 0f
                    var notified = false
                    val ended = drag(lp.id) { c ->
                        val delta = c.positionChange()
                        state.moveBy(delta)
                        moved += delta.getDistance()
                        c.consume()
                        if (!notified && moved > viewConfiguration.touchSlop) {
                            notified = true
                            movedState.value?.invoke()
                        }
                    }
                    if (ended && moved <= viewConfiguration.touchSlop) {
                        state.cancel()
                        longPress?.invoke()
                    } else if (ended) {
                        state.drop()
                    } else {
                        state.cancel()
                    }
                }
            }
        }
}
