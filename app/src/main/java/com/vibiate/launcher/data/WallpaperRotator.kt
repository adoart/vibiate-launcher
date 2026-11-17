package com.vibiate.launcher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vibiate.launcher.R
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schedules wallpaper rotations via AlarmManager (WorkManager's 15-minute
 * granularity can't hit clock times) and applies one-shot images.
 */
class WallpaperRotator(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val alarmManager get() =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // App-lifetime scope: a picked single wallpaper is applied even though
    // the sheet that launched the picker is gone by then.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Apply a just-picked single image, turn rotation off so the next
     * rotation cannot replace it. Non-suspend; safe to call from the
     * activity-result callback after the sheet is disposed.
     */
    fun applySingle(uri: Uri) {
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    WallpaperManager.getInstance(context).setStream(stream)
                    true
                } ?: false
            }.getOrDefault(false)
            if (ok) {
                settings.setWallpaperEnabled(false)
                reschedule()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, R.string.wallpaper_failed, Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WallpaperAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Recompute the next due time and (re)arm the alarm. Cancels everything
     * when rotation is disabled, the list is empty, or the schedule has no
     * future slot. Always cleans up the legacy WorkManager periodic job.
     */
    suspend fun reschedule() {
        // Legacy cleanup from the WorkManager-based implementation.
        workManager.cancelUniqueWork(PERIODIC_WORK)

        val enabled = settings.wallpaperEnabled.first()
        val images = settings.wallpaperImages.first()
        val schedule = settings.wallpaperSchedule.first()
        val next = if (enabled && images.isNotEmpty()) {
            nextRotation(schedule, LocalDateTime.now())
        } else {
            null
        }
        if (next == null) {
            alarmManager.cancel(alarmIntent())
            settings.setWallpaperNextDue(null)
            return
        }
        val epochMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        settings.setWallpaperNextDue(epochMs)
        val pi = alarmIntent()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
        }
    }

    suspend fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        alarmManager.cancel(alarmIntent())
        settings.setWallpaperNextDue(null)
    }

    /** Rotate to the next image now (worker reschedules afterwards). */
    fun rotateNow() {
        workManager.enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WallpaperRotateWorker>().build(),
        )
    }

    /** Set a specific rotation image as the wallpaper now. */
    fun setNow(uri: String) {
        workManager.enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WallpaperRotateWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(WallpaperRotateWorker.KEY_URI, uri)
                        .build(),
                )
                .build(),
        )
    }

    /**
     * Called from MainActivity.onStart: if a scheduled rotation was missed
     * (process death, missed alarm) fire it now; otherwise just re-arm —
     * covers reboots and exact-alarm permission changes.
     */
    suspend fun onLauncherStarted() {
        val due = settings.wallpaperNextDue.first()
        if (
            settings.wallpaperEnabled.first() && due != null &&
            System.currentTimeMillis() >= due
        ) {
            rotateNow()
        } else {
            reschedule()
        }
    }

    companion object {
        const val PERIODIC_WORK = "vibiate_wallpaper_rotation"
        const val ONE_SHOT_WORK = "vibiate_wallpaper_rotate_now"
    }
}
