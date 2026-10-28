package com.vibiate.launcher.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.vibiate.launcher.data.HOME_SCREEN_ID

/**
 * One default Home widget slot to bind: the first installed candidate
 * provider wins. [cols]/[rows] is the seeded grid span.
 */
data class SeedSpec(
    val cols: Int,
    val rows: Int,
    val candidates: List<ComponentName>,
)

internal fun ComponentName.flatten() = "$packageName/$className"

/**
 * Default Home slots, in order: Google clock, At a glance, Google search.
 * Candidates are flattened "pkg/cls" provider names, first installed wins.
 */
val DEFAULT_HOME_SLOTS: List<Triple<Int, Int, List<String>>> = listOf(
    Triple(
        4, 2,
        listOf(
            "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider",
            "com.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider",
        ),
    ),
    Triple(
        4, 1,
        listOf(
            "com.google.android.googlequicksearchbox/" +
                "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider",
        ),
    ),
    Triple(
        4, 1,
        listOf(
            "com.google.android.googlequicksearchbox/" +
                "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider",
            "com.google.android.googlequicksearchbox/" +
                "com.google.android.googlequicksearchbox.SearchWidgetProvider",
        ),
    ),
)

/**
 * Pick the provider to seed per slot: the first installed candidate, skipping
 * a slot when any of its candidates is already on Home. Keeps slot order.
 */
fun pickDefaultHomeWidgets(
    installed: Set<String>,
    presentOnHome: Set<String>,
    slots: List<Triple<Int, Int, List<String>>> = DEFAULT_HOME_SLOTS,
): List<Triple<String, Int, Int>> =
    slots.mapNotNull { (cols, rows, candidates) ->
        if (candidates.any { it in presentOnHome }) return@mapNotNull null
        val provider = candidates.firstOrNull { it in installed }
            ?: return@mapNotNull null
        Triple(provider, cols, rows)
    }

/** Android wrapper: resolve installed providers + current Home entries. */
fun pickDefaultHomeSpecs(
    context: Context,
    controller: WidgetController,
): List<SeedSpec> {
    val manager = AppWidgetManager.getInstance(context)
    val installed = manager.installedProviders
        .map { it.provider.flatten() }
        .toSet()
    val present = controller.widgets.value
        .filter { it.screenId == HOME_SCREEN_ID }
        .map { "${it.providerPackage}/${it.providerClass}" }
        .toSet()
    return pickDefaultHomeWidgets(installed, present)
        .mapNotNull { (flat, cols, rows) ->
            ComponentName.unflattenFromString(flat)
                ?.let { SeedSpec(cols, rows, listOf(it)) }
        }
}
