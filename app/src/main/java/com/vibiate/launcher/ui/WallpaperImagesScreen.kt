package com.vibiate.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vibiate.launcher.R
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.WallpaperImage
import com.vibiate.launcher.data.WallpaperImages
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.data.WallpaperTarget
import com.vibiate.launcher.data.addWallpaperImages
import com.vibiate.launcher.data.coverCrop
import com.vibiate.launcher.data.fitCropToAspect
import com.vibiate.launcher.data.moveItem
import com.vibiate.launcher.ui.theme.AviateBlue
import kotlinx.coroutines.launch

/** Manager for the rotation list: reorder, remove, crop-edit, add images. */
@Composable
fun WallpaperImagesScreen(
    settings: SettingsRepository,
    wallpaperRotator: WallpaperRotator,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persisted by settings.wallpaperImages.collectAsState(initial = null)
    val currentUri by settings.wallpaperCurrent.collectAsState(initial = null)
    val wpTarget by settings.wallpaperTarget.collectAsState(
        initial = WallpaperTarget.BOTH
    )

    // Local copy so drag-reorder doesn't fight the datastore round-trip.
    var images by remember { mutableStateOf<List<WallpaperImage>?>(null) }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(persisted) {
        if (draggingUri == null) images = persisted
    }
    val list = images.orEmpty()

    var editing by remember { mutableStateOf<WallpaperImage?>(null) }

    fun persist(l: List<WallpaperImage>) {
        scope.launch {
            settings.setWallpaperImages(l)
            wallpaperRotator.reschedule()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            val merged = addWallpaperImages(
                list, uris.map { it.toString() },
            )
            images = merged
            persist(merged)
        }
    }

    val screenAspect = LocalConfiguration.current.let {
        it.screenWidthDp.toFloat() / it.screenHeightDp
    }

    editing?.let { img ->
        androidx.activity.compose.BackHandler { editing = null }
        WallpaperCropEditor(
            image = img,
            frameAspect = screenAspect,
            onCancel = { editing = null },
            onSave = { crop ->
                val l = list.map {
                    if (it.uri == img.uri) it.copy(crop = crop) else it
                }
                images = l
                persist(l)
                editing = null
            },
            onSetNow = { crop ->
                val l = list.map {
                    if (it.uri == img.uri) it.copy(crop = crop) else it
                }
                images = l
                scope.launch {
                    settings.setWallpaperImages(l)
                    wallpaperRotator.setNow(img.uri)
                }
                editing = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(sectionBandColor())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(AviateBlue),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.wp_manager_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (persisted != null && list.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.wp_empty),
                    color = rowTextColor(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                }) {
                    Text(stringResource(R.string.wp_add_images))
                }
            }
            return@Column
        }

        val gridState = rememberLazyGridState()
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var gridSize by remember { mutableStateOf(IntSize.Zero) }
        // pointerInput callbacks freeze their captured values; keep a fresh
        // reference to the list or drops persist the pre-drag order.
        val currentList by androidx.compose.runtime.rememberUpdatedState(list)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .onSizeChanged { gridSize = it },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = list.size + 1,
                key = { i -> if (i < list.size) list[i].uri else "add" },
            ) { index ->
                if (index == list.size) {
                    // Add-images tile.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(screenAspect)
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowColor())
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts
                                            .PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = rowIconColor(),
                            )
                            Text(
                                stringResource(R.string.wp_add_images),
                                fontSize = 12.sp,
                                color = rowTextColor(),
                            )
                        }
                    }
                } else {
                    val img = list[index]
                    val isDragging = draggingUri == img.uri
                    WallpaperImageTile(
                        image = img,
                        position = index + 1,
                        frameAspect = screenAspect,
                        nowShowing = img.uri == currentUri,
                        elevated = isDragging,
                        offset = if (isDragging) dragOffset else Offset.Zero,
                        onTap = { editing = img },
                        onRemove = {
                            runCatching {
                                context.contentResolver
                                    .releasePersistableUriPermission(
                                        android.net.Uri.parse(img.uri),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                            }
                            val l = currentList.filterNot { it.uri == img.uri }
                            images = l
                            persist(l)
                        },
                        onDragStart = {
                            draggingUri = img.uri
                            dragOffset = Offset.Zero
                        },
                        onDrag = { delta ->
                            dragOffset += delta
                            val l = currentList
                            val from = l.indexOfFirst { it.uri == draggingUri }
                            if (from < 0) return@WallpaperImageTile
                            val item = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == from }
                                ?: return@WallpaperImageTile
                            val center = Offset(
                                item.offset.x + item.size.width / 2f +
                                    dragOffset.x,
                                item.offset.y + item.size.height / 2f +
                                    dragOffset.y,
                            )
                            val target = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull {
                                    it.index < l.size &&
                                        center.x >= it.offset.x &&
                                        center.x < it.offset.x + it.size.width &&
                                        center.y >= it.offset.y &&
                                        center.y < it.offset.y + it.size.height
                                }
                            if (target != null && target.index != from) {
                                images = moveItem(l, from, target.index)
                                // The item's slot moved; correct the
                                // accumulated offset so the tile stays
                                // under the finger.
                                dragOffset += Offset(
                                    (item.offset.x - target.offset.x).toFloat(),
                                    (item.offset.y - target.offset.y).toFloat(),
                                )
                            }
                        },
                        onDragEnd = {
                            draggingUri = null
                            dragOffset = Offset.Zero
                            persist(currentList)
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperImageTile(
    image: WallpaperImage,
    position: Int,
    frameAspect: Float,
    nowShowing: Boolean,
    elevated: Boolean,
    offset: Offset,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(
        initialValue = null,
        image.uri, image.crop, frameAspect,
    ) {
        val bmp = WallpaperImages.loadPreview(context, image.uri, 640)
        value = bmp?.let { cropForFrame(it, image.crop, frameAspect) }
            ?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .aspectRatio(frameAspect)
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                shadowElevation = if (elevated) 12.dp.toPx() else 0f
                alpha = if (elevated) 0.95f else 1f
            }
            .clip(RoundedCornerShape(8.dp))
            .background(rowColor())
            .pointerInput(image.uri) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, delta ->
                        change.consume()
                        onDrag(delta)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .clickable(onClick = onTap),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Unreadable image: placeholder, still removable.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = rowIconColor(),
                )
            }
        }
        Text(
            position.toString(),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (nowShowing) {
            Text(
                stringResource(R.string.wp_now_showing),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        AviateBlue.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.wp_remove_image),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Crop [bmp] per [crop] (cover-crop to [frameAspect] when null). */
private fun cropForFrame(
    bmp: Bitmap,
    crop: com.vibiate.launcher.data.CropRect?,
    frameAspect: Float,
): Bitmap {
    val imgAspect = bmp.width.toFloat() / bmp.height
    val c = crop?.let {
        fitCropToAspect(it, imgAspect, frameAspect)
    } ?: coverCrop(imgAspect, frameAspect)
    val l = (c.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
    val t = (c.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
    val w = ((c.right - c.left) * bmp.width).toInt()
        .coerceIn(1, bmp.width - l)
    val h = ((c.bottom - c.top) * bmp.height).toInt()
        .coerceIn(1, bmp.height - t)
    return Bitmap.createBitmap(bmp, l, t, w, h)
}
