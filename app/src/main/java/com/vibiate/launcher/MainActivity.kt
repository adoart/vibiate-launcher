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
import androidx.lifecycle.lifecycleScope
import com.vibiate.launcher.data.HOME_SCREEN_ID
import com.vibiate.launcher.ui.LauncherRoot
import com.vibiate.launcher.widget.SeedSpec
import com.vibiate.launcher.widget.pickDefaultHomeSpecs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as VibiateApplication

    /** Emits on each HOME press delivered while this activity is in front. */
    private val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            app.appRepository.invalidate()
            app.dockRepository.invalidate()
            app.iconPackManager.invalidate(intent.data?.schemeSpecificPart)
        }
    }

    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onWidgetBindResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.appRepository.ensureLoaded()

        // Seed Google's default Home widgets once per install/upgrade.
        lifecycleScope.launch {
            app.widgetController.restoredFlow.first { it }
            if (!app.settingsRepository.homeWidgetsSeeded.first()) {
                seedHomeWidgets(
                    pickDefaultHomeSpecs(this@MainActivity, app.widgetController)
                )
            }
        }

        // Seed each default space's app icons once (spaces' grids only get
        // shortcuts, never widgets, from this path).
        lifecycleScope.launch {
            app.widgetController.restoredFlow.first { it }
            app.appRepository.loaded.first { it }
            if (app.settingsRepository.spacesContentSeeded.first()) {
                return@launch
            }
            val spaceIds = app.settingsRepository.spaces.first()
                .map { it.id }.toSet()
            val widgets = app.widgetController.widgets.value
            val shortcuts = app.widgetController.shortcuts.value
            val apps = app.appRepository.apps.value
            for (id in listOf(1, 2, 3, 4)) {
                if (id !in spaceIds) continue
                val screenId = com.vibiate.launcher.data.spaceScreenId(id)
                if (widgets.any { it.screenId == screenId } ||
                    shortcuts.any { it.screenId == screenId }
                ) {
                    continue
                }
                app.widgetController.setScreenApps(
                    screenId,
                    com.vibiate.launcher.data.defaultSpaceApps(id, apps),
                )
            }
            app.settingsRepository.setSpacesContentSeeded(true)
        }

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
                spaceEngine = app.spaceEngine,
                wallpaperRotator = app.wallpaperRotator,
                dockRepository = app.dockRepository,
                homeEvents = homeEvents,
                onAddWidget = ::addWidgetFromPicker,
                onRestoreHomeWidgets = ::restoreHomeWidgets,
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

    /** Seeded span for the in-flight add; 0 = auto from provider info. */
    private var pendingCols = 0
    private var pendingRows = 0

    /** Allocated host id for the in-flight pick; -1 when no flow is active. */
    private var pendingWidgetId = -1

    /** Provider seen during the pick/bind steps, kept for the config step. */
    private var pendingProvider: ComponentName? = null

    /** Default-widget specs still to seed; empty when no seed flow runs. */
    private val seedQueue = ArrayDeque<SeedSpec>()
    private var seeding = false

    /**
     * Queue default Home widgets and bind them one at a time through the same
     * bind/configure state machine as a manual add. The Home-screen flag is
     * persisted when the queue drains so the user isn't prompted every launch.
     */
    fun seedHomeWidgets(specs: List<SeedSpec>) {
        if (specs.isEmpty() || seeding) {
            if (!seeding) markSeeded()
            return
        }
        seedQueue.clear()
        seedQueue.addAll(specs)
        seeding = true
        startNextSeed()
    }

    private fun startNextSeed() {
        val spec = seedQueue.firstOrNull()
        if (spec == null) {
            seeding = false
            markSeeded()
            return
        }
        seedQueue.removeFirst()
        pendingScreenId = HOME_SCREEN_ID
        pendingCols = spec.cols
        pendingRows = spec.rows
        pendingProvider = spec.candidates.first()
        pendingWidgetId = app.widgetController.allocateId()
        val provider = spec.candidates.first()
        if (app.widgetController.bindIfAllowed(pendingWidgetId, provider)) {
            finishWidgetAdd(pendingWidgetId)
        } else {
            bindWidgetLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            )
        }
    }

    private fun markSeeded() {
        lifecycleScope.launch { app.settingsRepository.setHomeWidgetsSeeded(true) }
    }

    /** Settings row: re-seed any default Home widget slots that are missing. */
    fun restoreHomeWidgets() {
        seedHomeWidgets(pickDefaultHomeSpecs(this, app.widgetController))
    }

    /**
     * The in-app picker chose a widget: allocate an id and go straight to
     * bind (the same path seeding uses), carrying the picker's span so the
     * placed widget matches the preview.
     */
    fun addWidgetFromPicker(
        screenId: Int,
        provider: ComponentName,
        cols: Int,
        rows: Int,
    ) {
        if (seeding) return
        pendingScreenId = screenId
        pendingCols = cols
        pendingRows = rows
        pendingProvider = provider
        val id = app.widgetController.allocateId()
        pendingWidgetId = id
        if (app.widgetController.bindIfAllowed(id, provider)) {
            finishWidgetAdd(id)
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
            app.widgetController.addWidget(
                id, provider, pendingScreenId, pendingCols, pendingRows,
            )
        } else {
            app.widgetController.discardId(id)
        }
        endWidgetFlow()
    }

    private fun endWidgetFlow() {
        pendingWidgetId = -1
        pendingProvider = null
        pendingCols = 0
        pendingRows = 0
        // A cancelled/skipped seed just moves to the next queued spec.
        if (seeding) startNextSeed()
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
            app.widgetController.addWidget(
                id, provider, pendingScreenId, pendingCols, pendingRows,
            )
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
