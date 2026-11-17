package com.vibiate.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.CropRect
import com.vibiate.launcher.data.WallpaperImage
import com.vibiate.launcher.data.WallpaperImages
import com.vibiate.launcher.data.coverCrop
import com.vibiate.launcher.data.fitCropToAspect
import com.vibiate.launcher.data.panCrop
import com.vibiate.launcher.data.zoomCrop
import kotlin.math.roundToInt

/**
 * Full-screen crop/placement editor: the white frame (screen aspect) shows
 * what the wallpaper will look like; pan to move, pinch to zoom, double-tap
 * to toggle cover <-> 2.5x zoom. Back/Cancel discards.
 */
@Composable
fun WallpaperCropEditor(
    image: WallpaperImage,
    frameAspect: Float,
    onCancel: () -> Unit,
    onSave: (CropRect) -> Unit,
    onSetNow: (CropRect) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null, image.uri,
    ) {
        value = WallpaperImages.loadPreview(context, image.uri, 2048)
            ?.asImageBitmap()
    }
    val bmp = bitmap
    val imageAspect = bmp?.let { it.width.toFloat() / it.height } ?: 1f
    // Recompute once the bitmap loads: before that imageAspect is a
    // placeholder and any crop built from it would have the wrong
    // pixel aspect.
    var crop by remember(imageAspect) {
        mutableStateOf(
            image.crop?.let {
                fitCropToAspect(it, imageAspect, frameAspect)
            } ?: coverCrop(imageAspect, frameAspect),
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
        ) {
            // Frame = largest rect of screen aspect fitting the area.
            val areaW = constraints.maxWidth.toFloat()
            val areaH = constraints.maxHeight.toFloat()
            val fw: Float
            val fh: Float
            if (areaW / areaH > frameAspect) {
                fh = areaH; fw = areaH * frameAspect
            } else {
                fw = areaW; fh = areaW / frameAspect
            }
            val fl = (areaW - fw) / 2f
            val ft = (areaH - fh) / 2f

            var canvasMod: Modifier = Modifier.fillMaxSize()
            if (bmp != null) {
                canvasMod = canvasMod
                    .pointerInput(frameAspect, imageAspect) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val cw = crop.right - crop.left
                            val ch = crop.bottom - crop.top
                            crop = panCrop(
                                crop,
                                -pan.x / fw * cw,
                                -pan.y / fh * ch,
                            )
                            val cw2 = crop.right - crop.left
                            val ch2 = crop.bottom - crop.top
                            val fx = crop.left +
                                (centroid.x - fl) / fw * cw2
                            val fy = crop.top +
                                (centroid.y - ft) / fh * ch2
                            crop = zoomCrop(
                                crop, zoom, fx, fy,
                                imageAspect, frameAspect,
                            )
                        }
                    }
                    .pointerInput(frameAspect, imageAspect) {
                        detectTapGestures(
                            onDoubleTap = { pos ->
                                val cover = coverCrop(
                                    imageAspect, frameAspect,
                                )
                                val zoomed = (crop.right - crop.left) <
                                    (cover.right - cover.left) - 0.001f
                                crop = if (zoomed) {
                                    cover
                                } else {
                                    val fx = crop.left +
                                        (pos.x - fl) / fw *
                                        (crop.right - crop.left)
                                    val fy = crop.top +
                                        (pos.y - ft) / fh *
                                        (crop.bottom - crop.top)
                                    zoomCrop(
                                        crop, 2.5f, fx, fy,
                                        imageAspect, frameAspect,
                                    )
                                }
                            },
                        )
                    }
            }
            Canvas(canvasMod) {
                if (bmp != null) {
                    val cw = crop.right - crop.left
                    val ch = crop.bottom - crop.top
                    // Draw the image so the crop rect maps onto the frame.
                    val imgW = fw / cw
                    val imgH = fh / ch
                    val dstOffset = IntOffset(
                        (fl - crop.left * imgW).roundToInt(),
                        (ft - crop.top * imgH).roundToInt(),
                    )
                    val dstSize = IntSize(
                        imgW.roundToInt(), imgH.roundToInt(),
                    )
                    val srcSize = IntSize(bmp.width, bmp.height)
                    drawImage(
                        bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = srcSize,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                    )
                    // Dim everything, then redraw undimmed inside the frame.
                    drawRect(Color.Black.copy(alpha = 0.6f))
                    clipRect(fl, ft, fl + fw, ft + fh) {
                        drawImage(
                            bmp,
                            srcOffset = IntOffset.Zero,
                            srcSize = srcSize,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                        )
                    }
                }
                drawRect(
                    Color.White,
                    topLeft = Offset(fl, ft),
                    size = androidx.compose.ui.geometry.Size(fw, fh),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (bmp == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TextButton(
                enabled = bmp != null,
                onClick = {
                    crop = coverCrop(imageAspect, frameAspect)
                },
            ) {
                Text(
                    stringResource(R.string.wp_reset),
                    color = Color.White,
                )
            }
            TextButton(
                enabled = bmp != null,
                onClick = {
                    Toast.makeText(
                        context, R.string.wp_set_toast, Toast.LENGTH_SHORT,
                    ).show()
                    onSetNow(crop)
                },
            ) {
                Text(
                    stringResource(R.string.wp_set_now),
                    color = Color.White,
                )
            }
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.cancel),
                    color = Color.White,
                )
            }
            TextButton(
                enabled = bmp != null,
                onClick = { onSave(crop) },
            ) {
                Text(
                    stringResource(R.string.wp_save),
                    color = Color.White,
                )
            }
        }
    }
}
