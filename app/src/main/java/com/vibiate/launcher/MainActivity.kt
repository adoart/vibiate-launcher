package com.vibiate.launcher

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.vibiate.launcher.ui.LauncherRoot
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val app get() = application as VibiateApplication

    /** Emits on each HOME press delivered while this activity is in front. */
    private val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            app.appRepository.invalidate()
            app.dockRepository.invalidate()
        }
    }

    private val pickWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onWidgetPickResult(result)
        }

    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onWidgetBindResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.appRepository.ensureLoaded()

        // The theme's android:windowShowWallpaper is not always honored; the
        // runtime flag is the reliable equivalent.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            Context.RECEIVER_EXPORTED,
        )

        setContent {
            LauncherRoot(
                appRepository = app.appRepository,
                settings = app.settingsRepository,
                iconPackManager = app.iconPackManager,
                widgetController = app.widgetController,
                wallpaperRotator = app.wallpaperRotator,
                dockRepository = app.dockRepository,
                homeEvents = homeEvents,
                onAddWidget = ::startWidgetPick,
            )
        }
    }

    // singleTask: pressing HOME with the launcher in front re-delivers the
    // home intent here instead of recreating the activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            homeEvents.tryEmit(Unit)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // ---- App widget pick/bind flow ----

    /** Screen that requested the in-flight pick; 0 = built-in Widgets page. */
    private var pendingScreenId = 0

    private fun startWidgetPick(screenId: Int) {
        pendingScreenId = screenId
        val id = app.widgetController.allocateId()
        pickWidgetLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        )
    }

    private fun onWidgetPickResult(result: ActivityResult) {
        val data = result.data
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val provider = data?.providerComponent()
        if (result.resultCode != RESULT_OK || id == -1 || provider == null) {
            if (id != -1) app.widgetController.discardId(id)
            return
        }
        if (app.widgetController.bindIfAllowed(id, provider)) {
            app.widgetController.addWidget(id, provider, pendingScreenId)
        } else {
            bindWidgetLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            )
        }
    }

    private fun onWidgetBindResult(result: ActivityResult) {
        val data = result.data
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        // The bind result doesn't reliably echo EXTRA_APPWIDGET_PROVIDER; once bound,
        // AppWidgetManager can resolve the provider for us.
        val provider = data?.providerComponent()
            ?: if (id != -1) app.widgetController.providerInfo(id)?.provider else null
        if (result.resultCode == RESULT_OK && id != -1 && provider != null) {
            app.widgetController.addWidget(id, provider, pendingScreenId)
        } else if (id != -1) {
            app.widgetController.discardId(id)
        }
    }

    private fun Intent.providerComponent(): ComponentName? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        }

    companion object {
        const val REQUEST_PICK_APPWIDGET = 0x501
        const val REQUEST_BIND_APPWIDGET = 0x502
    }
}
