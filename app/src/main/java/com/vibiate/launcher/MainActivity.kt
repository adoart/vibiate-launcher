package com.vibiate.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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

    /** Allocated host id for the in-flight pick; -1 when no flow is active. */
    private var pendingWidgetId = -1

    /** Provider seen during the pick/bind steps, kept for the config step. */
    private var pendingProvider: ComponentName? = null

    private fun startWidgetPick(screenId: Int) {
        pendingScreenId = screenId
        pendingWidgetId = app.widgetController.allocateId()
        pickWidgetLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        )
    }

    private fun onWidgetPickResult(result: ActivityResult) {
        val data = result.data
        // A cancelled pick returns null data (or appWidgetId=0); the pending
        // fallback is required or the allocated id would leak.
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it > 0 } ?: pendingWidgetId
        val provider = data?.providerComponent()
            ?: app.widgetController.providerInfo(id)?.provider
        pendingProvider = provider
        if (result.resultCode != RESULT_OK) {
            app.widgetController.discardId(id)
            endWidgetFlow()
            return
        }
        if (app.widgetController.providerInfo(id) != null) {
            // The Settings picker bound the widget itself before returning.
            finishWidgetAdd(id)
        } else if (provider != null) {
            if (app.widgetController.bindIfAllowed(id, provider)) {
                finishWidgetAdd(id)
            } else {
                bindWidgetLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                )
            }
        } else {
            app.widgetController.discardId(id)
            endWidgetFlow()
        }
    }

    private fun onWidgetBindResult(result: ActivityResult) {
        val data = result.data
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it > 0 } ?: pendingWidgetId
        // The bind result doesn't reliably echo EXTRA_APPWIDGET_PROVIDER; once bound,
        // AppWidgetManager can resolve the provider for us.
        val provider = data?.providerComponent()
            ?: app.widgetController.providerInfo(id)?.provider
        if (result.resultCode == RESULT_OK && id != -1 && provider != null) {
            pendingProvider = provider
            finishWidgetAdd(id)
        } else if (id != -1) {
            app.widgetController.discardId(id)
            endWidgetFlow()
        }
    }

    /**
     * Widget is bound: run the provider's configure activity when it needs one,
     * otherwise add it to the requesting screen immediately.
     */
    private fun finishWidgetAdd(id: Int) {
        val info = app.widgetController.providerInfo(id)
        val configOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info != null &&
            (info.widgetFeatures and
                AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info?.configure != null && !configOptional) {
            val launched = runCatching {
                app.widgetController.host.startAppWidgetConfigureActivityForResult(
                    this, id, 0, REQUEST_CONFIGURE_APPWIDGET, null,
                )
            }.isSuccess
            // The configure result arrives in onActivityResult.
            if (launched) return
            app.widgetController.discardId(id)
            endWidgetFlow()
            return
        }
        val provider = info?.provider ?: pendingProvider
        if (provider != null) {
            app.widgetController.addWidget(id, provider, pendingScreenId)
        } else {
            app.widgetController.discardId(id)
        }
        endWidgetFlow()
    }

    private fun endWidgetFlow() {
        pendingWidgetId = -1
        pendingProvider = null
    }

    // startAppWidgetConfigureActivityForResult delivers through the legacy
    // onActivityResult channel, not an ActivityResultContract.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_APPWIDGET) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it > 0 } ?: pendingWidgetId
        val provider = app.widgetController.providerInfo(id)?.provider
            ?: pendingProvider
        if (resultCode == RESULT_OK && id != -1 && provider != null) {
            app.widgetController.addWidget(id, provider, pendingScreenId)
        } else if (id != -1) {
            app.widgetController.discardId(id)
        }
        endWidgetFlow()
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
        const val REQUEST_CONFIGURE_APPWIDGET = 0x503
    }
}
