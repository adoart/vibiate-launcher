package com.vibiate.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibiate.launcher.VibiateApplication

/** Manifest-registered fallback; MainActivity also registers one at runtime. */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? VibiateApplication ?: return
        app.appRepository.invalidate()
    }
}
