package com.vibiate.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Sets the home/lock wallpaper to the next image in the user's rotation list,
 * then advances the stored index. No-ops gracefully when the list is empty or a
 * URI can no longer be read.
 */
class WallpaperRotateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(applicationContext)
        val uris = settings.wallpaperUris.first()
        if (uris.isEmpty()) return@withContext Result.success()

        val index = settings.wallpaperIndex.first()
        val uriString = uris[index % uris.size]
        val applied = applyWallpaper(Uri.parse(uriString))

        // Advance the cycle even if this image failed, so a dead URI doesn't stall rotation.
        settings.setWallpaperIndex((index + 1) % uris.size)
        if (applied) Result.success() else Result.retry()
    }

    private fun applyWallpaper(uri: Uri): Boolean = runCatching {
        applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
            WallpaperManager.getInstance(applicationContext).setBitmap(bitmap)
            Unit
        } ?: return false
        true
    }.getOrDefault(false)
}
