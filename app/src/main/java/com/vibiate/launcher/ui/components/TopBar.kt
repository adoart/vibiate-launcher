package com.vibiate.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.ui.theme.LocalAviateColors
import kotlinx.coroutines.delay

/** Ordinal == pager page index. Custom screens and the add-page follow these. */
enum class LauncherTab { SPACES, HOME, WIDGETS, COLLECTIONS, ALL_APPS }

/**
 * Aviate's thin page indicator: a short flat line under the status bar in the
 * current page's segment. Visible only while the pager is moving (swipe or
 * programmatic scroll): fades in fast, holds ~600ms after settling, fades out.
 * The 8dp slot is always reserved so content never jumps.
 */
@Composable
fun LauncherPageIndicator(
    pagerState: PagerState,
    /** Real pages only — excludes the trailing "Add space" page. */
    pageCount: Int,
) {
    var visible by remember { mutableStateOf(false) }
    val scrolling = pagerState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) {
            visible = true
        } else {
            delay(600)
            visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 150 else 300),
        label = "pageIndicator",
    )
    val barColor = LocalAviateColors.current.homeText.copy(alpha = 0.9f)
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(8.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val segments = pageCount.coerceAtLeast(1)
            val barPx = widthPx / segments
            Box(
                Modifier
                    .width(with(
                        androidx.compose.ui.platform.LocalDensity.current,
                    ) { barPx.toDp() })
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    // x read in the offset/layout phase so swipes don't
                    // recompose the indicator every frame.
                    .offset {
                        val pos = (
                            pagerState.currentPage +
                                pagerState.currentPageOffsetFraction
                            ).coerceIn(0f, (segments - 1).toFloat())
                        IntOffset((pos * barPx).toInt(), 0)
                    }
                    .background(barColor),
            )
        }
    }
}
