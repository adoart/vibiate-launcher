package com.vibiate.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.launcherDragGestures

/** Opens the app's system details screen. */
internal fun openAppDetails(context: Context, app: AppEntry) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun Drawable.toImageBitmap(sizePx: Int = 168): ImageBitmap? = runCatching {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()

/**
 * App icon + optional label. Resolves themed icons from the active icon pack,
 * falling back to the app's own icon. Long-press runs [onLongPress], or opens
 * system app details when it is null. When [dragPayload] is set, the icon is
 * draggable: [onDragMoved] fires once past touch slop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    iconTheme: IconTheme?,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
    /** When false, renders inert (the parent owns the gestures). */
    interactive: Boolean = true,
    dragPayload: (() -> DragPayload)? = null,
    onDragMoved: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    labelSize: TextUnit = 11.sp,
    labelColor: Color = Color.Unspecified,
    onLaunch: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(app, iconTheme) {
        iconTheme?.bitmapFor(app.component, app.icon) ?: app.icon.toImageBitmap()
    }

    val gestures = when {
        !interactive -> Modifier
        dragPayload != null -> Modifier.launcherDragGestures(
            payload = dragPayload,
            onTap = onLaunch,
            onLongPressNoMove = onLongPress ?: { openAppDetails(context, app) },
            onDragMoved = onDragMoved,
        )
        else -> Modifier.combinedClickable(
            onClick = onLaunch,
            onLongClick = {
                if (onLongPress != null) onLongPress() else openAppDetails(context, app)
            },
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(gestures)
            .padding(4.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(iconSize),
            )
        }
        if (showLabel) {
            Text(
                text = app.label,
                fontSize = labelSize,
                color = if (labelColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    labelColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
