package com.vibiate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackTest {

    private fun filter(vararg tags: Pair<String, Map<String, String>>): AppFilter {
        val builder = AppFilterBuilder()
        tags.forEach { (name, attrs) -> builder.onTag(name, attrs) }
        return builder.build()
    }

    private fun item(component: String, drawable: String) =
        "item" to mapOf("component" to component, "drawable" to drawable)

    // ---- <item> parsing ----

    @Test
    fun `item maps ComponentInfo component to normalized key`() {
        val f = filter(
            item("ComponentInfo{com.a/com.a.Main}", "a_main"),
            item("com.b/.Sub", "b_sub"),           // leading "." expands to pkg
            item("com.c/com.c.Raw", "c_raw"),      // raw form without ComponentInfo
        )
        assertEquals("a_main", f.components["com.a/com.a.Main"])
        assertEquals("b_sub", f.components["com.b/com.b.Sub"])
        assertEquals("c_raw", f.components["com.c/com.c.Raw"])
    }

    @Test
    fun `duplicate component entries keep the first drawable`() {
        val f = filter(
            item("ComponentInfo{com.a/com.a.Main}", "first"),
            item("ComponentInfo{com.a/com.a.Main}", "second"),
        )
        assertEquals("first", f.components["com.a/com.a.Main"])
    }

    @Test
    fun `package fallback maps pkg to first seen drawable`() {
        val f = filter(
            item("com.a/com.a.One", "one"),
            item("com.a/com.a.Two", "two"),
            item("com.b/com.b.One", "b_one"),
        )
        assertEquals("one", f.packages["com.a"])
        assertEquals("b_one", f.packages["com.b"])
    }

    @Test
    fun `malformed item entries are dropped`() {
        val f = filter(
            item("nocomponent", "x"),              // no '/' -> dropped
            "item" to mapOf("component" to "com.a/com.a.M"),  // no drawable
            "item" to mapOf("drawable" to "d"),              // no component
            item("ComponentInfo{com.x/.Y}", "xy"),
        )
        assertEquals(1, f.components.size)
        assertEquals("xy", f.components["com.x/com.x.Y"])
    }

    // ---- <calendar> ----

    @Test
    fun `calendar records prefix per component and package`() {
        val f = filter(
            "calendar" to mapOf(
                "component" to "ComponentInfo{com.cal/com.cal.AllInOne}",
                "prefix" to "calendar_",
            ),
            "calendar" to mapOf(
                "component" to "ComponentInfo{com.cal/com.cal.Other}",
                "prefix" to "cal2_",
            ),
        )
        assertEquals("calendar_", f.calendars["com.cal/com.cal.AllInOne"])
        assertEquals("cal2_", f.calendars["com.cal/com.cal.Other"])
        // first prefix seen for a package wins
        assertEquals("calendar_", f.calendarPackages["com.cal"])
    }

    // ---- mask/back/upon/scale ----

    @Test
    fun `iconback collects imgN attrs in order`() {
        val f = filter(
            "iconback" to mapOf(
                "img2" to "back2",
                "img1" to "back1",
                "img3" to "back3",
            ),
        )
        assertEquals(listOf("back1", "back2", "back3"), f.iconBacks)
    }

    @Test
    fun `iconmask and iconupon read img1`() {
        val f = filter(
            "iconmask" to mapOf("img1" to "mask_a"),
            "iconupon" to mapOf("img1" to "upon_a"),
        )
        assertEquals("mask_a", f.iconMask)
        assertEquals("upon_a", f.iconUpon)
    }

    @Test
    fun `scale defaults to 1 and clamps to range`() {
        assertEquals(1f, filter().scale, 0.001f)
        assertEquals(
            0.8f,
            filter("scale" to mapOf("factor" to "0.8")).scale,
            0.001f,
        )
        assertEquals(
            2f,
            filter("scale" to mapOf("factor" to "9")).scale,
            0.001f,
        )
    }

    @Test
    fun `hasMask truth table`() {
        assertFalse(filter().hasMask)
        assertTrue(filter("scale" to mapOf("factor" to "0.9")).hasMask)
        assertTrue(filter("iconback" to mapOf("img1" to "b")).hasMask)
        assertTrue(filter("iconmask" to mapOf("img1" to "m")).hasMask)
        assertTrue(filter("iconupon" to mapOf("img1" to "u")).hasMask)
    }

    // ---- icon override serialization ----

    @Test
    fun `icon overrides round-trip including original`() {
        val map = mapOf(
            "com.a/com.a.Main" to IconOverride.Pack("com.pack", "icon_a"),
            "com.b/com.b.Home" to IconOverride.Original,
        )
        val raw = SettingsRepository.serializeIconOverrides(map)
        assertEquals(map, SettingsRepository.parseIconOverrides(raw))
    }

    @Test
    fun `icon override parse drops malformed lines`() {
        val raw = """
            com.a/com.a.Main=com.pack|icon_a
            no-equals-sign
            com.c/com.c.C=|
            com.d/com.d.D=onlypkg
            =com.pack|x
            com.b/com.b.Home=@original
        """.trimIndent()
        val parsed = SettingsRepository.parseIconOverrides(raw)
        assertEquals(2, parsed.size)
        assertEquals(
            IconOverride.Pack("com.pack", "icon_a"),
            parsed["com.a/com.a.Main"],
        )
        assertEquals(IconOverride.Original, parsed["com.b/com.b.Home"])
    }

    @Test
    fun `componentToKey handles ComponentInfo and leading dot`() {
        assertEquals(
            "com.a/com.a.Main",
            AppFilterBuilder.componentToKey("ComponentInfo{com.a/com.a.Main}"),
        )
        assertEquals("com.b/com.b.Sub", AppFilterBuilder.componentToKey("com.b/.Sub"))
        assertNull(AppFilterBuilder.componentToKey("noslash"))
        assertNull(AppFilterBuilder.componentToKey("com.a/"))
    }
}
