package com.vibiate.launcher.data

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks the HOME-screen wallpaper's colours (API 27+) and derives the
 * launcher accent from them. Reacts to rotations, single picks and changes
 * made by other apps via WallpaperManager's colours-changed listener.
 */
class WallpaperAccent(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Derived accent, or null when unknown / unsupported. */
    private val _accent = MutableStateFlow<Int?>(null)
    val accent: StateFlow<Int?> = _accent

    private val listener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.OnColorsChangedListener { colors, which ->
                if (colors != null &&
                    which and WallpaperManager.FLAG_SYSTEM != 0
                ) {
                    apply(colors)
                }
            }
        } else {
            null
        }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        runCatching {
            listener?.let {
                WallpaperManager.getInstance(app).addOnColorsChangedListener(
                    it, Handler(Looper.getMainLooper()),
                )
            }
        }
        refresh()
    }

    /** Re-read the wallpaper colours (catch-up after missed callbacks). */
    fun refresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        scope.launch {
            val colors = runCatching {
                WallpaperManager.getInstance(app)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
            if (colors != null) apply(colors)
        }
    }

    private fun apply(colors: WallpaperColors) {
        _accent.value = accentFromWallpaper(
            colors.primaryColor.toArgb(),
            colors.secondaryColor?.toArgb(),
            colors.tertiaryColor?.toArgb(),
        )
    }
}
