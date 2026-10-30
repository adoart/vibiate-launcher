package com.vibiate.launcher.ui.drag

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragDropTest {

    @Test
    fun drop_commits_only_past_slop() {
        val start = Offset(100f, 100f)
        assertFalse(shouldCommitDrop(start, start, 20f))
        assertFalse(shouldCommitDrop(start, Offset(110f, 105f), 20f))
        assertTrue(shouldCommitDrop(start, Offset(121f, 100f), 20f))
        assertTrue(shouldCommitDrop(start, Offset(100f, 500f), 20f))
    }
}
