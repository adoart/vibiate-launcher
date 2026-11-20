package com.vibiate.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.ui.theme.LocalAviateColors

private val railLetters: List<Char> = ('A'..'Z').toList() + '#'

/**
 * Vertical A–Z index rail on the right edge, Aviate-style: every letter is
 * shown; letters with apps are emphasized. A hairline divider sits on the
 * rail's left edge. Tapping or dragging emits the letter at that position —
 * the caller resolves absent letters to the nearest present group.
 */
@Composable
fun AlphabetRail(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var railHeight by remember { mutableStateOf(1) }
    val divider = LocalAviateColors.current.divider
    val present = LocalAviateColors.current.cardLabel
    val density = LocalDensity.current

    fun indexFor(y: Float): Int =
        ((y / railHeight.toFloat()) * railLetters.size)
            .toInt()
            .coerceIn(0, railLetters.size - 1)

    Row(modifier = modifier.fillMaxHeight()) {
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(divider),
        )
        // Equal-weight slots: each letter's touch bin is exactly railHeight/27
        // and the glyph is sized to fit inside it. Fonts with tall metrics
        // used to overflow the SpaceEvenly layout and drift off their bins.
        val slotHeightPx = railHeight.toFloat() / railLetters.size
        val fitSize = with(density) { (slotHeightPx * 0.7f).toSp() }
        val letterSize = if (fitSize < 12.sp) fitSize else 12.sp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { railHeight = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    detectTapGestures { offset ->
                        onLetterSelected(railLetters[indexFor(offset.y)])
                    }
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures { change, _ ->
                        onLetterSelected(railLetters[indexFor(change.position.y)])
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            railLetters.forEach { letter ->
                val hasApps = letter in letters
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        fontSize = letterSize,
                        lineHeight = letterSize,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (hasApps) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (hasApps) {
                            present
                        } else {
                            present.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                    )
                }
            }
        }
    }
}
