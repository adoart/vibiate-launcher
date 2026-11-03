package com.vibiate.launcher.widget

import android.content.ComponentName

/** One provider an app offers in the widget picker. */
data class WidgetOption(
    val provider: ComponentName,
    val appPackage: String,
    val appLabel: String,
    val label: String,
    val description: String?,
    val span: GridSpan,
)

/** An app's collapsible panel in the picker. */
data class WidgetGroup(
    val appPackage: String,
    val appLabel: String,
    val widgets: List<WidgetOption>,
)

/** Group by package; groups and widgets sorted by label (case-insensitive). */
fun groupWidgets(options: List<WidgetOption>): List<WidgetGroup> =
    options.groupBy { it.appPackage }
        .map { (pkg, ws) ->
            WidgetGroup(
                pkg, ws.first().appLabel,
                ws.sortedBy { it.label.lowercase() },
            )
        }
        .sortedBy { it.appLabel.lowercase() }

/**
 * Blank query → unchanged. Else keep a group when the app name matches (all
 * its widgets) or any widget's label/description matches (only those).
 */
fun filterWidgets(groups: List<WidgetGroup>, query: String): List<WidgetGroup> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return groups
    return groups.mapNotNull { g ->
        if (g.appLabel.lowercase().contains(q)) {
            g
        } else {
            g.copy(
                widgets = g.widgets.filter {
                    it.label.lowercase().contains(q) ||
                        it.description?.lowercase()?.contains(q) == true
                },
            ).takeIf { it.widgets.isNotEmpty() }
        }
    }
}

/** Uniform scale ≤ 1 fitting width×height inside maxW×maxH (aspect kept). */
fun fitPreview(widthPx: Int, heightPx: Int, maxW: Float, maxH: Float): Float {
    if (widthPx <= 0 || heightPx <= 0) return 1f
    return minOf(1f, minOf(maxW / widthPx, maxH / heightPx))
}
