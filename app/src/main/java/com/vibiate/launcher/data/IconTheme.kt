package com.vibiate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Per-app icon choice: force a specific pack icon, or force the original. */
sealed interface IconOverride {
    data class Pack(val packPackage: String, val drawable: String) : IconOverride
    data object Original : IconOverride
}

/**
 * The composed icon resolution used by all UI: primary pack, ordered
 * fallbacks, per-app overrides, and optional masking of unthemed icons.
 * A null primary with no fallbacks and no overrides behaves as a pure
 * pass-through to the original icon.
 */
class IconTheme(
    private val context: Context,
    val primary: IconPack?,
    /** Fallback packs in user order (primary excluded). */
    val fallbacks: List<IconPack>,
    /** componentKey -> per-app choice. */
    private val overrides: Map<String, IconOverride>,
    private val maskUnthemed: Boolean,
    private val manager: IconPackManager,
) {
    private val dayOfMonth: Int
        get() = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    private val bitmapCache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun drawableFor(component: ComponentName, original: Drawable?): Drawable? {
        val key = "${component.packageName}/${component.className}"
        when (val override = overrides[key]) {
            is IconOverride.Pack ->
                manager.loadPack(override.packPackage)
                    ?.loadDrawable(override.drawable)?.let { return it }
            // falls through to packs when the override's drawable is gone
            IconOverride.Original -> return original
            null -> Unit
        }
        primary?.themedDrawable(component, dayOfMonth)?.let { return it }
        for (pack in fallbacks) {
            pack.themedDrawable(component, dayOfMonth)?.let { return it }
        }
        if (maskUnthemed && original != null && primary?.hasMask == true) {
            composite(primary, key, original)?.let { return it }
        }
        return original
    }

    fun bitmapFor(component: ComponentName, original: Drawable?): ImageBitmap? {
        // Day in the key so dynamic-calendar icons roll over at midnight.
        val key = "${component.packageName}/${component.className}#$dayOfMonth"
        bitmapCache.get(key)?.let { return it }
        val bitmap = drawableFor(component, original)
            ?.let { drawableToBitmap(it, ICON_SIZE_PX)?.asImageBitmap() }
            ?: return null
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    /** Resolve and rasterize icons ahead of first paint; call off-main. */
    fun prewarm(apps: List<AppEntry>) {
        for (app in apps) bitmapFor(app.component, app.icon)
    }

    /**
     * Standard ADW/Nova unthemed-icon compositing at [S] px: draw the original
     * scaled by `scale`, cut it with iconMask (DST_OUT), slide iconBacks under
     * it (DST_OVER), then paint iconUpon on top.
     */
    private fun composite(pack: IconPack, componentKey: String, original: Drawable): Drawable? {
        val filter = pack.filter
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val scaled = (S * filter.scale).roundToInt().coerceIn(1, S)
        val off = (S - scaled) / 2
        original.setBounds(off, off, off + scaled, off + scaled)
        original.draw(canvas)

        val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        filter.iconMask?.let { name ->
            pack.loadDrawable(name)
                ?.let { drawableToBitmap(it, S) }
                ?.let { canvas.drawBitmap(it, null, Rect(0, 0, S, S), outPaint) }
        }
        if (filter.iconBacks.isNotEmpty()) {
            val back = filter.iconBacks[
                abs(componentKey.hashCode()) % filter.iconBacks.size
            ]
            val underPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            }
            pack.loadDrawable(back)
                ?.let { drawableToBitmap(it, S) }
                ?.let { canvas.drawBitmap(it, null, Rect(0, 0, S, S), underPaint) }
        }
        filter.iconUpon?.let { name ->
            pack.loadDrawable(name)
                ?.let { drawableToBitmap(it, S) }
                ?.let { canvas.drawBitmap(it, null, Rect(0, 0, S, S), null) }
        }
        return BitmapDrawable(context.resources, bmp)
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap? =
        runCatching {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bmp
        }.getOrNull()

    private companion object {
        const val S = 168
        const val ICON_SIZE_PX = 168
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
