package com.vibiate.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vibiate.launcher.R
import com.vibiate.launcher.data.AppEntry
import com.vibiate.launcher.data.AppRepository
import com.vibiate.launcher.data.DockRepository
import com.vibiate.launcher.data.HOME_SCREEN_ID
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.IconTheme
import com.vibiate.launcher.data.LauncherFont
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.data.ThemeMode
import com.vibiate.launcher.data.WallpaperRotator
import com.vibiate.launcher.ui.components.LauncherDock
import com.vibiate.launcher.ui.components.LauncherTab
import com.vibiate.launcher.ui.components.LauncherTopBar
import com.vibiate.launcher.ui.components.toImageBitmap
import com.vibiate.launcher.ui.drag.DragDropState
import com.vibiate.launcher.ui.drag.DragPayload
import com.vibiate.launcher.ui.drag.LocalDragDrop
import com.vibiate.launcher.ui.theme.LocalAviateColors
import com.vibiate.launcher.ui.theme.VibiateTheme
import com.vibiate.launcher.widget.WidgetController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(
    appRepository: AppRepository,
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    widgetController: WidgetController,
    wallpaperRotator: WallpaperRotator,
    dockRepository: DockRepository,
    /** Emits when HOME is pressed while the launcher is in front. */
    homeEvents: Flow<Unit>,
    /** Start the widget pick flow for the given screen id (0 = built-in Widgets). */
    onAddWidget: (Int) -> Unit,
    /** Settings -> "Restore default Home widgets". */
    onRestoreHomeWidgets: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
    val iconPackPkg by settings.iconPackPackage.collectAsState(initial = "")
    val gridSize by settings.gridSize.collectAsState(
        initial = SettingsRepository.DEFAULT_GRID_SIZE
    )
    val fontFamily by settings.fontFamily.collectAsState(initial = LauncherFont.AVIATE)

    val iconPackFallbacks by settings.iconPackFallbacks.collectAsState(
        initial = emptyList()
    )
    val maskUnthemed by settings.maskUnthemedIcons.collectAsState(initial = true)
    val iconOverrides by settings.iconOverrides.collectAsState(initial = emptyMap())
    val iconPackRevision by iconPackManager.revision.collectAsState()

    // Built off the main thread (appfilter parses can touch 50k entries) and
    // prewarmed so the icon swap doesn't stutter. The old theme is kept until
    // the new one is ready — resetting to null between keys would flicker
    // every icon back to the system drawable on each settings change.
    var iconTheme by remember { mutableStateOf<IconTheme?>(null) }
    LaunchedEffect(
        iconPackPkg, iconPackFallbacks, maskUnthemed, iconOverrides,
        iconPackRevision,
    ) {
        iconTheme = withContext(Dispatchers.Default) {
            val theme = IconTheme(
                context = context.applicationContext,
                primary = iconPackPkg.takeIf { it.isNotEmpty() }
                    ?.let { iconPackManager.loadPack(it) },
                fallbacks = iconPackFallbacks
                    .mapNotNull { iconPackManager.loadPack(it) },
                overrides = iconOverrides,
                maskUnthemed = maskUnthemed,
                manager = iconPackManager,
            )
            theme.prewarm(appRepository.apps.value)
            theme
        }
    }

    // Shared drag session: one for every page + the root-level ghost.
    val drag = remember { DragDropState() }

    // Pages: built-in tabs (ordinal order: HOME, WIDGETS, COLLECTIONS, ALL_APPS),
    // then custom widget spaces, then a trailing "Add space" page.
    val customScreens by settings.customScreens.collectAsState(initial = emptyList())
    val builtInCount = LauncherTab.entries.size
    val pagerState = rememberPagerState(initialPage = 0) {
        builtInCount + customScreens.size + 1
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCustomize by rememberSaveable { mutableStateOf(false) }
    // Each composed page reports editing independently — adjacent pages
    // would otherwise overwrite each other's onEditingChange(false).
    val widgetEditingByPage = remember { mutableStateMapOf<Int, Boolean>() }
    val widgetEditing = widgetEditingByPage.values.any { it }
    // OxygenOS-style backdrop blur while a widget is edited/moved.
    val editBlur by animateFloatAsState(
        targetValue = if (widgetEditing) 1f else 0f,
        animationSpec = tween(200),
        label = "editBlur",
    )

    // Removing a space shrinks the pager; keep currentPage within bounds.
    LaunchedEffect(pagerState.pageCount) {
        val last = pagerState.pageCount - 1
        if (pagerState.currentPage > last) pagerState.scrollToPage(last)
    }

    // Widget host listens while the activity is started.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> widgetController.startListening()
                Lifecycle.Event.ON_STOP -> widgetController.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            widgetController.stopListening()
        }
    }

    fun launch(app: AppEntry) {
        context.startActivity(appRepository.launchIntent(app))
    }

    // Emitted once per Collections-search-bar tap, after the pager settles on
    // ALL_APPS; the A–Z screen focuses its field on each emission.
    val azSearchFocus = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    // HOME key while in front: close overlays, return to the home page.
    LaunchedEffect(Unit) {
        homeEvents.collect {
            showCustomize = false
            showSettings = false
            pagerState.animateScrollToPage(LauncherTab.HOME.ordinal)
        }
    }

    // Launchers swallow back on home; sub-screens pop back to the home page.
    BackHandler {
        when {
            showCustomize -> showCustomize = false
            showSettings -> showSettings = false
            pagerState.currentPage != LauncherTab.HOME.ordinal ->
                scope.launch { pagerState.animateScrollToPage(LauncherTab.HOME.ordinal) }
            else -> Unit
        }
    }

    VibiateTheme(themeMode, font = fontFamily) {
        CompositionLocalProvider(LocalDragDrop provides drag) {
        val aviate = LocalAviateColors.current
        // Backdrop intensity: 0 on Home/Widgets/custom spaces, full on
        // Collections and A–Z, interpolated while the pager slides.
        val pagerPos = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val backdropFraction = when {
            pagerPos <= LauncherTab.COLLECTIONS.ordinal ->
                (pagerPos - LauncherTab.WIDGETS.ordinal).coerceIn(0f, 1f)
            pagerPos <= LauncherTab.ALL_APPS.ordinal -> 1f
            else -> (1f - (pagerPos - LauncherTab.ALL_APPS.ordinal)).coerceIn(0f, 1f)
        }

        // Cross-window wallpaper blur while editing (API 31+). The window
        // needs a transparent background drawable for the blur to show.
        val window = remember {
            var c: Context? = context
            while (c is ContextWrapper && c !is Activity) c = c.baseContext
            (c as? Activity)?.window
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowManager = remember {
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            var blurEnabled by remember {
                mutableStateOf(windowManager.isCrossWindowBlurEnabled)
            }
            DisposableEffect(windowManager) {
                val listener = java.util.function.Consumer<Boolean> {
                    blurEnabled = it
                }
                windowManager.addCrossWindowBlurEnabledListener(listener)
                blurEnabled = windowManager.isCrossWindowBlurEnabled
                onDispose {
                    windowManager.removeCrossWindowBlurEnabledListener(listener)
                }
            }
            LaunchedEffect(editBlur, blurEnabled) {
                window?.let { w ->
                    if (blurEnabled && editBlur > 0f) {
                        w.setBackgroundDrawable(
                            android.graphics.drawable.ColorDrawable(
                                android.graphics.Color.TRANSPARENT
                            )
                        )
                        w.setBackgroundBlurRadius((60f * editBlur).toInt())
                    } else {
                        w.setBackgroundBlurRadius(0)
                    }
                }
            }
        }

        // Collections/A–Z use a strong skrim instead of a wallpaper blur.
        // (Window background blur only works with windowIsTranslucent, which
        // the edit-mode blur above relies on.)
        val skrimColor = aviate.pageSkrimStrong
            .copy(alpha = aviate.pageSkrimStrong.alpha * backdropFraction)

        // Transparent background: the system wallpaper shows through.
        Surface(
            Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
        Box(Modifier.fillMaxSize()) {
            // "Blurred" Aviate backdrop under Collections/A–Z.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(skrimColor),
            )
            // Top gradient keeps the top bar legible over bright wallpaper.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(194.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x66000000), Color.Transparent),
                        ),
                    ),
            )
            // Dim scrim over the wallpaper while editing; launcher chrome is
            // blurred per-sibling so the selected widget stays sharp.
            if (editBlur > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f * editBlur)),
                )
            }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.blur(16.dp * editBlur)) {
                LauncherTopBar(
                    // Custom/add pages and settings: underline nothing.
                    activeTab = pagerState.currentPage
                        .takeIf { it in LauncherTab.entries.indices && !showSettings }
                        ?.let { LauncherTab.entries[it] },
                    onTabSelected = { tab ->
                        showSettings = false
                        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                    },
                    onSettingsClick = { showSettings = true },
                )
                }
                // Long-press on empty space anywhere opens the customize sheet.
                // Icons/widgets consume their own long-presses first (Main pass
                // runs child-to-parent), so only unhandled gaps reach this.
                Box(
                    Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!showSettings) showCustomize = true
                                }
                            )
                        }
                ) {
                    if (showSettings) {
                        // Settings is a full opaque page; pad for the hidden dock.
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            SettingsScreen(
                                settings = settings,
                                iconPackManager = iconPackManager,
                                appRepository = appRepository,
                                dockRepository = dockRepository,
                                wallpaperRotator = wallpaperRotator,
                                onBack = { showSettings = false },
                                onRestoreHomeWidgets = onRestoreHomeWidgets,
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !drag.isDragging && !widgetEditing,
                            // Keep the drag-source page (e.g. A–Z) composed while
                            // the pager animates to Collections mid-drag, or its
                            // gesture coroutine is cancelled.
                            beyondViewportPageCount = 1,
                        ) { page ->
                            when {
                                page < builtInCount -> when (LauncherTab.entries[page]) {
                                    LauncherTab.HOME -> HomeScreen(
                                        settings = settings,
                                        controller = widgetController,
                                        editBlur = editBlur,
                                        onEditingChange = {
                                            widgetEditingByPage[page] = it
                                        },
                                    )
                                    LauncherTab.WIDGETS -> WidgetsScreen(
                                        controller = widgetController,
                                        screenId = 0,
                                        editBlur = editBlur,
                                        onEditingChange = {
                                            widgetEditingByPage[page] = it
                                        },
                                    )
                                    LauncherTab.COLLECTIONS -> CollectionsScreen(
                                        repository = appRepository,
                                        settings = settings,
                                        iconTheme = iconTheme,
                                        iconPackManager = iconPackManager,
                                        gridSize = gridSize,
                                        onLaunch = ::launch,
                                        onSearchClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(
                                                    LauncherTab.ALL_APPS.ordinal,
                                                )
                                                azSearchFocus.emit(Unit)
                                            }
                                        },
                                    )
                                    LauncherTab.ALL_APPS -> AllAppsScreen(
                                        repository = appRepository,
                                        settings = settings,
                                        iconPackManager = iconPackManager,
                                        iconTheme = iconTheme,
                                        onLaunch = ::launch,
                                        focusRequests = azSearchFocus,
                                        onDragToCollections = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(
                                                    LauncherTab.COLLECTIONS.ordinal,
                                                )
                                            }
                                        },
                                    )
                                }
                                page < builtInCount + customScreens.size -> {
                                    val screen = customScreens[page - builtInCount]
                                    WidgetsScreen(
                                        controller = widgetController,
                                        screenId = screen.id,
                                        screenName = screen.name,
                                        editBlur = editBlur,
                                        onEditingChange = {
                                            widgetEditingByPage[page] = it
                                        },
                                        onRemoveScreen = {
                                            widgetController.removeScreen(screen.id)
                                        },
                                    )
                                }
                                else -> AddScreenPage(
                                    onAdd = {
                                        widgetController.addScreen { id ->
                                            context.getString(R.string.space_name, id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                // Dock persists under all pager pages; hidden while settings is open.
                if (!showSettings) {
                    Box(Modifier.blur(16.dp * editBlur)) {
                        LauncherDock(
                            repository = dockRepository,
                            iconTheme = iconTheme,
                        )
                    }
                }
            }
        }

        if (showCustomize) {
            CustomizeSheet(
                settings = settings,
                iconPackManager = iconPackManager,
                appRepository = appRepository,
                dockRepository = dockRepository,
                wallpaperRotator = wallpaperRotator,
                gridSize = gridSize,
                fontFamily = fontFamily,
                onDismiss = { showCustomize = false },
                onOpenSettings = {
                    showCustomize = false
                    showSettings = true
                },
                onAddWidget = {
                    showCustomize = false
                    // Target: Home -> HOME_SCREEN_ID; the Widgets page -> 0;
                    // a custom space -> its id; anywhere else -> Widgets page
                    // after paging to it.
                    val page = pagerState.currentPage
                    val target = when {
                        page == LauncherTab.HOME.ordinal -> HOME_SCREEN_ID
                        page == LauncherTab.WIDGETS.ordinal -> 0
                        page in builtInCount until builtInCount + customScreens.size ->
                            customScreens[page - builtInCount].id
                        else -> 0
                    }
                    if (page != LauncherTab.HOME.ordinal &&
                        page != LauncherTab.WIDGETS.ordinal &&
                        page !in builtInCount until builtInCount + customScreens.size
                    ) {
                        scope.launch {
                            pagerState.animateScrollToPage(LauncherTab.WIDGETS.ordinal)
                        }
                    }
                    onAddWidget(target)
                },
            )
        }

        // Drag ghost, above everything and untouchable: it follows the finger.
        DragGhost(iconTheme = iconTheme)
        }
        }
    }
}

/**
 * Floating preview of the in-flight drag, drawn in root coordinates so it
 * tracks the finger even as the pager slides pages underneath. No pointer
 * input — drops are resolved by [CollectionsScreen]'s tracked bounds.
 */
@Composable
private fun DragGhost(iconTheme: IconTheme?) {
    val drag = LocalDragDrop.current
    val payload = drag.payload ?: return
    val pointer = drag.pointer
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        when (payload) {
            is DragPayload.App -> {
                val bitmap = remember(payload.app, iconTheme) {
                    iconTheme?.bitmapFor(payload.app.component, payload.app.icon)
                        ?: payload.app.icon.toImageBitmap()
                }
                val sizePx = with(density) { 56.dp.roundToPx() }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (pointer.x - sizePx / 2).roundToInt(),
                                (pointer.y - sizePx / 2).roundToInt(),
                            )
                        }
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            shadowElevation = 24f
                        },
                ) {
                    bitmap?.let { Image(it, contentDescription = null) }
                }
            }
            is DragPayload.Category -> {
                val aviate = LocalAviateColors.current
                val heightPx = with(density) { 52.dp.roundToPx() }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, (pointer.y - heightPx / 2).roundToInt()) }
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(2.dp)),
                    shape = RoundedCornerShape(2.dp),
                    color = aviate.cardBackground.copy(
                        alpha = maxOf(aviate.cardBackground.alpha, 0.85f),
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            imageVector = payload.def.icon,
                            contentDescription = null,
                            tint = aviate.cardIcon,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = payload.def.title.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp,
                            color = aviate.cardTitle,
                        )
                    }
                }
            }
        }
    }
}
