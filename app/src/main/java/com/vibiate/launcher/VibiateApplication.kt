package com.vibiate.launcher

import android.app.Application
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.widget.WidgetController

class VibiateApplication : Application() {

    val settingsRepository by lazy { SettingsRepository(this) }
    val appRepository by lazy { AppRepository(this) }
    val dockRepository by lazy { DockRepository(this) }
    val iconPackManager by lazy { IconPackManager(this) }
    val widgetController by lazy { WidgetController(this, settingsRepository) }
    val wallpaperRotator by lazy { WallpaperRotator(this, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
        appRepository.ensureLoaded()
    }
}
