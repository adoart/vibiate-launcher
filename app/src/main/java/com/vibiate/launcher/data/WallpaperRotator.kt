package com.vibiate.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Schedules/cancels the periodic wallpaper rotation worker. A no-op schedule when
 * the user has not picked any images.
 */
class WallpaperRotator(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Reschedule based on current settings. Call after enabling or changing interval/URIs. */
    suspend fun reschedule() {
        val enabled = settings.wallpaperEnabled.first()
        val uris = settings.wallpaperUris.first()
        val interval = settings.wallpaperIntervalMinutes.first()
        if (!enabled || uris.isEmpty()) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<WallpaperRotateWorker>(interval, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    fun rotateNow() {
        workManager.enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WallpaperRotateWorker>().build(),
        )
    }

    /**
     * One-shot wallpaper set from a just-picked URI, independent of the rotation
     * list. Decodes on IO and applies via WallpaperManager; returns success.
     */
    suspend fun setOnce(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return@use false
                WallpaperManager.getInstance(context).setBitmap(bitmap)
                true
            } ?: false
        }.getOrDefault(false)
    }

    companion object {
        const val PERIODIC_WORK = "vibiate_wallpaper_rotation"
        const val ONE_SHOT_WORK = "vibiate_wallpaper_rotate_now"
    }
}
