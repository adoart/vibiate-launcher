package com.vibiate.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibiate.launcher.VibiateApplication

/** AlarmManager wake-up for a scheduled wallpaper rotation. */
class WallpaperAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as VibiateApplication)
            .wallpaperRotator.rotateNow()
    }
}
