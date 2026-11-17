package com.vibiate.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.DisplayMetrics
import android.util.LruCache
import android.media.ExifInterface
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Decoding and crop-aware rendering for rotation wallpapers. */
object WallpaperImages {

    // Small thumbnail cache for the manager grid.
    private val previewCache = LruCache<String, Bitmap>(12)

    /** Downsampled, EXIF-oriented preview; null when the uri can't be read. */
    suspend fun loadPreview(
        context: Context,
        uri: String,
        maxDim: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        previewCache.get(uri)?.let { return@withContext it }
        val bmp = decodeSampled(context, uri, maxDim)
        if (bmp != null) previewCache.put(uri, bmp)
        bmp
    }

    /**
     * Render [image] to exactly [width] x [height] using its crop rect
     * (coverCrop when null). Decodes only enough resolution for the cropped
     * region. Null when the image can't be read.
     */
    suspend fun renderWallpaper(
        context: Context,
        image: WallpaperImage,
        width: Int,
        height: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val dims = orientedDimensions(context, image.uri) ?: return@withContext null
        val (imgW, imgH) = dims
        if (imgW <= 0 || imgH <= 0) return@withContext null
        val frameAspect = width.toFloat() / height
        val crop = image.crop?.let {
            fitCropToAspect(it, imgW.toFloat() / imgH, frameAspect)
        } ?: coverCrop(imgW.toFloat() / imgH, frameAspect)
        val cropWpx = ((crop.right - crop.left) * imgW).coerceAtLeast(1f)
        val cropHpx = ((crop.bottom - crop.top) * imgH).coerceAtLeast(1f)
        // Sample so the cropped region decodes at >= target resolution.
        val neededScale = max(
            width / cropWpx, height / cropHpx,
        ).coerceAtMost(1f)
        val maxNeeded = (max(imgW, imgH) * neededScale).roundToInt()
            .coerceAtLeast(1)
        val oriented = decodeSampled(context, image.uri, maxNeeded)
            ?: return@withContext null
        val s = oriented.width.toFloat() / imgW // sampling scale
        val l = (crop.left * imgW * s).roundToInt()
            .coerceIn(0, oriented.width - 1)
        val t = (crop.top * imgH * s).roundToInt()
            .coerceIn(0, oriented.height - 1)
        val w = (cropWpx * s).roundToInt().coerceIn(1, oriented.width - l)
        val h = (cropHpx * s).roundToInt().coerceIn(1, oriented.height - t)
        val cropped = Bitmap.createBitmap(oriented, l, t, w, h)
        val out = Bitmap.createScaledBitmap(cropped, width, height, true)
        if (cropped !== oriented) oriented.recycle()
        if (out !== cropped) cropped.recycle()
        out
    }

    /** Portrait real display size (min side x max side). */
    fun wallpaperTargetSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            min(b.width(), b.height()) to max(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            min(dm.widthPixels, dm.heightPixels) to
                max(dm.widthPixels, dm.heightPixels)
        }
    }

    /** Oriented (post-EXIF-rotation) pixel size, without a full decode. */
    private fun orientedDimensions(
        context: Context,
        uri: String,
    ): Pair<Int, Int>? = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, android.net.Uri.parse(uri))
            var size: Pair<Int, Int>? = null
            // Tiny throwaway decode just to read the oriented dimensions.
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                size = info.size.width to info.size.height
                decoder.setTargetSize(1, 1)
            }
            size
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(android.net.Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            var w = opts.outWidth
            var h = opts.outHeight
            if (w <= 0 || h <= 0) return@runCatching null
            val rotation = exifRotation(context, uri)
            if (rotation == 90 || rotation == 270) {
                val tmp = w; w = h; h = tmp
            }
            w to h
        }
    }.getOrNull()

    /** Decode the uri downsampled so max dimension <= [maxDim], EXIF-applied. */
    private fun decodeSampled(
        context: Context,
        uri: String,
        maxDim: Int,
    ): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val parsed = android.net.Uri.parse(uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, parsed)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val scale = maxDim.toFloat() / max(s.width, s.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        max(1, (s.width * scale).roundToInt()),
                        max(1, (s.height * scale).roundToInt()),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null
            applyExifRotation(context, parsed, raw)
        }
    }.getOrNull()

    private fun exifRotation(context: Context, uri: String): Int = runCatching {
        context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
            when (
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun applyExifRotation(
        context: Context,
        uri: android.net.Uri,
        bmp: Bitmap,
    ): Bitmap {
        val rotation = exifRotation(context, uri.toString())
        if (rotation == 0) return bmp
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }
}
