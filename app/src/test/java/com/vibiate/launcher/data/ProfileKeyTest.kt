package com.vibiate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Work-profile keys ("pkg/activity@serial") through every key parser. */
class ProfileKeyTest {

    private val own = profileKey("com.google.android.gm", "com.google.android.gm.Main", null)
    private val work = profileKey("com.google.android.gm", "com.google.android.gm.Main", 10)

    @Test
    fun ownKeyKeepsLegacyFormat() {
        assertEquals("com.google.android.gm/com.google.android.gm.Main", own)
        assertEquals(own to null, splitProfileKey(own))
    }

    @Test
    fun workKeyCarriesSerialAndDiffersFromTwin() {
        assertEquals("com.google.android.gm/com.google.android.gm.Main@10", work)
        assertNotEquals(own, work)
        assertEquals(own to 10L, splitProfileKey(work))
    }

    @Test
    fun nonNumericSuffixIsNotASerial() {
        val k = "pkg/pkg.Main@beta"
        assertEquals(k to null, splitProfileKey(k))
    }

    @Test
    fun normalizeDockKeyKeepsSerial() {
        assertEquals("pkg/pkg.Main@10", normalizeDockKey("pkg/.Main@10"))
        assertEquals(work, normalizeDockKey(work))
    }

    @Test
    fun dockDecodeKeepsWorkAndPersonalTwins() {
        val slots = decodeDockApps(listOf(own, work, "", work).joinToString("\n"))!!
        assertEquals(listOf(own, work, null, null), slots.take(4))
        assertEquals(slots, decodeDockApps(encodeDockApps(slots)))
    }

    @Test
    fun pageShortcutRoundTripsWorkKey() {
        val s = PageShortcut(3, work, -1, 1, 2)
        assertEquals(s, PageShortcut.deserialize(s.serialize()))
    }
}
