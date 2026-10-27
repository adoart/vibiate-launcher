package com.vibiate.launcher.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.DockEntry
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconTheme

private const val DRAG_THRESHOLD_PX = 40f

/**
 * Aviate-style dock: bare icons on the wallpaper over a soft bottom gradient,
 * a slim chevron handle, 5 icons always visible, and a second row of 5 that
 * expands via the handle or a quick vertical drag on the dock.
 */
@Composable
fun LauncherDock(
    repository: DockRepository,
    iconTheme: IconTheme?,
) {
    val entries by repository.entries.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.ensureLoaded() }

    // Bare icons on the wallpaper; a soft bottom gradient keeps them legible.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { total = 0f },
                        onDragCancel = { total = 0f },
                    ) { _, dragAmount ->
                        total += dragAmount
                        if (!expanded && total <= -DRAG_THRESHOLD_PX) {
                            expanded = true
                            total = 0f
                        } else if (expanded && total >= DRAG_THRESHOLD_PX) {
                            expanded = false
                            total = 0f
                        }
                    }
                }
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Slim centered chevron handle.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = stringResource(
                        if (expanded) R.string.dock_collapse else R.string.dock_expand
                    ),
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Second row (browser, camera, gallery, Play Store, files) expands upward.
            AnimatedVisibility(visible = expanded) {
                DockRow(entries.drop(5).take(5), iconTheme)
            }
            DockRow(entries.take(5), iconTheme)
        }
    }
}

@Composable
private fun DockRow(entries: List<DockEntry>, iconTheme: IconTheme?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        entries.forEach { entry ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DockIcon(entry, iconTheme)
            }
        }
    }
}

/** 48dp touch target, no label; long-press opens system app details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(entry: DockEntry, iconTheme: IconTheme?) {
    val context = LocalContext.current
    val bitmap = remember(entry, iconTheme) {
        entry.component?.let { iconTheme?.bitmapFor(it, entry.icon) }
            ?: entry.icon?.toImageBitmap()
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(
                onClick = { runCatching { context.startActivity(entry.intent) } },
                onLongClick = {
                    entry.component?.let { component ->
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${component.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = entry.label,
                modifier = Modifier.size(38.dp),
            )
        } else {
            Icon(
                imageVector = entry.glyph,
                contentDescription = entry.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
