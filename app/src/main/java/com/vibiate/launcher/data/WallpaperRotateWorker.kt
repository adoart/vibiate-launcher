package com.vibiate.launcher.data

import android.app.WallpaperManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Applies the next rotation image (or a forced uri via KEY_URI), honouring
 * crop, shuffle and the home/lock target, then re-arms the alarm. Unreadable
 * images are skipped (at most images.size attempts); no retry loops.
 */
class WallpaperRotateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(applicationContext)
        val images = settings.wallpaperImages.first()
        if (images.isNotEmpty()) {
            applyNext(settings, images)
        }
        WallpaperRotator(applicationContext, settings).reschedule()
        Result.success()
    }

    private suspend fun applyNext(
        settings: SettingsRepository,
        images: List<WallpaperImage>,
    ) {
        val forced = inputData.getString(KEY_URI)
        val target = settings.wallpaperTarget.first()
        val shuffle = settings.wallpaperShuffle.first()
        val current = settings.wallpaperCurrent.first()

        val order: List<WallpaperImage> = if (forced != null) {
            listOf(images.firstOrNull { it.uri == forced } ?: WallpaperImage(forced))
        } else {
            // Chosen next image first, then the rest in list order as fallbacks
            // for unreadable files (bounded by images.size).
            val first = nextWallpaperImage(images, current, shuffle, Random.Default)
                ?: return
            val start = images.indexOfFirst { it.uri == first.uri }
                .coerceAtLeast(0)
            (0 until images.size).map { images[(start + it) % images.size] }
        }

        val (w, h) = WallpaperImages.wallpaperTargetSize(applicationContext)
        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH ->
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        for (img in order) {
            val bmp = WallpaperImages.renderWallpaper(
                applicationContext, img, w, h,
            ) ?: continue
            val ok = runCatching {
                WallpaperManager.getInstance(applicationContext)
                    .setBitmap(bmp, null, true, flags)
            }.isSuccess
            bmp.recycle()
            if (ok) {
                settings.setWallpaperCurrent(img.uri)
                // Keep the wallpaper-derived accent in sync even if the
                // colours-changed listener is slow to fire.
                (applicationContext as? com.vibiate.launcher.VibiateApplication)
                    ?.wallpaperAccent?.refresh()
                return
            }
        }
    }

    companion object {
        const val KEY_URI = "uri"
    }
}
