package com.vibiate.launcher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.stringResource
import com.vibiate.launcher.R
import com.vibiate.launcher.data.openAppInfo
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

/** Opens the app's system details screen (in its own profile). */
internal fun openAppDetails(context: Context, app: AppEntry) {
    openAppInfo(context, app.component, app.user)
}

/**
 * Icon image with the work-profile treatment: a small briefcase badge at the
 * bottom-right for [isWork], greyed out while the work profile is [paused].
 */
@Composable
fun BadgedIconImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    isWork: Boolean,
    paused: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(iconSize)) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            colorFilter = if (paused) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            } else {
                null
            },
            modifier = Modifier
                .size(iconSize)
                .alpha(if (paused) 0.5f else 1f),
        )
        if (isWork) WorkBadge(iconSize / 3, Modifier.align(Alignment.BottomEnd))
    }
}

/** Small round briefcase marking a work-profile app. */
@Composable
fun WorkBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset(x = size / 6, y = size / 6)
            .size(size)
            .background(Color(0xFF37474F), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.Work,
            contentDescription = stringResource(R.string.work_app),
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
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
            BadgedIconImage(
                bitmap = bitmap,
                contentDescription = app.label,
                isWork = app.isWork,
                paused = app.paused,
                iconSize = iconSize,
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
