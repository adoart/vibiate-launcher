package com.vibiate.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.appwidget.AppWidgetHostView
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import com.vibiate.launcher.R
import com.vibiate.launcher.widget.WidgetController
import kotlin.math.ceil

/**
 * Widget space: vertically stacked bound widgets rendered through
 * AppWidgetHostView, each removable, plus an "Add widget" button.
 *
 * [screenId] 0 is the built-in Widgets page; custom spaces pass their
 * CustomScreen id and show a [screenName] header with a remove affordance
 * ([onRemoveScreen]).
 */
@Composable
fun WidgetsScreen(
    controller: WidgetController,
    screenId: Int,
    onAddWidget: () -> Unit,
    screenName: String? = null,
    onRemoveScreen: (() -> Unit)? = null,
) {
    val allWidgets by controller.widgets.collectAsState()
    val widgets = allWidgets.filter { it.screenId == screenId }
    var confirmRemove by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (screenName != null) {
            item(key = "screen_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = screenName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (onRemoveScreen != null) {
                        IconButton(onClick = { confirmRemove = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.remove_space),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(widgets, key = { it.appWidgetId }) { entry ->
            // Host views collapse without an explicit height; size them in
            // launcher-style cells like the provider asked for.
            val info = controller.providerInfo(entry.appWidgetId)
            val density = LocalDensity.current
            val cell = 96.dp
            val rows = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (info?.targetCellHeight ?: 0) > 0
                ) {
                    info!!.targetCellHeight
                } else {
                    val minHeightDp = info?.minHeight
                        ?.let { with(density) { it.toDp().value } } ?: 0f
                    ceil((minHeightDp + 16f) / cell.value).toInt()
                }
                ).coerceIn(1, 6)
            val itemHeight = cell * rows
            var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
            var reportedSize by remember { mutableStateOf<IntSize?>(null) }
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            val view = controller.createView(entry)
                                ?: android.widget.FrameLayout(ctx)
                            hostView = view as? AppWidgetHostView
                            view
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .onSizeChanged { size ->
                                // Report once per size; the widget then picks
                                // the matching responsive layout.
                                val view = hostView ?: return@onSizeChanged
                                if (reportedSize == size) return@onSizeChanged
                                reportedSize = size
                                val wDp = size.width / density.density
                                val hDp = size.height / density.density
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    view.updateAppWidgetSize(
                                        Bundle(),
                                        listOf(SizeF(wDp, hDp)),
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    view.updateAppWidgetSize(
                                        null,
                                        info?.let {
                                            (it.minWidth / density.density).toInt()
                                        } ?: wDp.toInt(),
                                        info?.let {
                                            (it.minHeight / density.density).toInt()
                                        } ?: hDp.toInt(),
                                        wDp.toInt(),
                                        hDp.toInt(),
                                    )
                                }
                            },
                    )
                    IconButton(
                        onClick = { controller.removeWidget(entry.appWidgetId) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove_widget),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (widgets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_widgets),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Button(onClick = onAddWidget) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.add_widget),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }

    if (confirmRemove && screenName != null && onRemoveScreen != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_space_title, screenName)) },
            text = { Text(stringResource(R.string.remove_space_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemoveScreen()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
