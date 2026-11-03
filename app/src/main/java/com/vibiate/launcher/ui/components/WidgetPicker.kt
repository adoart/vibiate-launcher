package com.vibiate.launcher.ui.components

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.widget.RemoteViews
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.vibiate.launcher.R
import com.vibiate.launcher.ui.theme.LocalIsDark
import com.vibiate.launcher.widget.GridSpan
import com.vibiate.launcher.widget.WidgetGroup
import com.vibiate.launcher.widget.WidgetOption
import com.vibiate.launcher.widget.autoSpan
import com.vibiate.launcher.widget.filterWidgets
import com.vibiate.launcher.widget.fitPreview
import com.vibiate.launcher.widget.groupWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CELL_HEIGHT_DP = 96f
private val GRID_GAP_DP = 8f
private val GRID_PAD_DP = 12f
private val COLUMNS = 4

/** Loaded once when the sheet opens: groups + provider info for previews. */
private class WidgetCatalog(
    val groups: List<WidgetGroup>,
    val infos: Map<ComponentName, AppWidgetProviderInfo>,
    val appIcons: Map<String, Drawable?>,
)

private fun loadCatalog(context: android.content.Context): WidgetCatalog {
    val pm = context.packageManager
    val mgr = AppWidgetManager.getInstance(context)
    // Same cell geometry WidgetsScreen lays out with.
    val screenWdp = context.resources.displayMetrics.widthPixels /
        context.resources.displayMetrics.density
    val cellWdp = (screenWdp - 2 * GRID_PAD_DP - (COLUMNS - 1) * GRID_GAP_DP) /
        COLUMNS
    val options = mutableListOf<WidgetOption>()
    val infos = mutableMapOf<ComponentName, AppWidgetProviderInfo>()
    val appIcons = mutableMapOf<String, Drawable?>()
    mgr.getInstalledProvidersForProfile(Process.myUserHandle())
        .filter {
            it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
        }
        .forEach { info ->
            val pkg = info.provider.packageName
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val appLabel = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            appIcons.putIfAbsent(pkg, runCatching { pm.getApplicationIcon(pkg) }.getOrNull())
            infos[info.provider] = info
            options += WidgetOption(
                provider = info.provider,
                appPackage = pkg,
                appLabel = appLabel,
                label = runCatching { info.loadLabel(pm) }.getOrDefault(""),
                description = if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { info.loadDescription(context)?.toString() }
                        .getOrNull()
                } else {
                    null
                },
                span = autoSpan(
                    info.minWidth.toFloat() /
                        context.resources.displayMetrics.density,
                    info.minHeight.toFloat() /
                        context.resources.displayMetrics.density,
                    info.targetCellWidth,
                    info.targetCellHeight,
                    cellWdp,
                    CELL_HEIGHT_DP,
                ),
            )
        }
    return WidgetCatalog(groupWidgets(options), infos, appIcons)
}

/** The picker's own widget gallery, styled after Pixel's picker. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetPickerSheet(
    onPick: (WidgetOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val sheetBg = if (isDark) Color(0xFF1B1C1F) else Color(0xFFF3F3F6)
    val cardBg = if (isDark) Color(0xFF2A2B2F) else Color.White
    val pillBg = if (isDark) Color(0xFF2E3034) else Color(0xFFE3E3E8)
    val primary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val secondary = if (isDark) Color(0xFFAAAAAE) else Color(0xFF5C5C60)

    val catalog by produceState<WidgetCatalog?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { loadCatalog(context) }
                .getOrElse { WidgetCatalog(emptyList(), emptyMap(), emptyMap()) }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val searching = query.isNotBlank()
    val shown = catalog?.let { filterWidgets(it.groups, query) }.orEmpty()

    ModalBottomSheet(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF8E9099)),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Search pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(pillBg)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = secondary,
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            color = secondary, fontSize = 17.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = primary, fontSize = 17.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (catalog == null) {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = secondary)
                }
            } else if (shown.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.no_widgets_found),
                        color = secondary, fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.appPackage }) { group ->
                        val idx = shown.indexOf(group)
                        val isOpen = searching ||
                            group.appPackage in expanded
                        WidgetGroupCard(
                            group = group,
                            catalog = catalog!!,
                            isOpen = isOpen,
                            cardBg = cardBg,
                            primary = primary,
                            secondary = secondary,
                            shape = cardShape(idx, shown.size),
                            onToggle = {
                                expanded =
                                    if (group.appPackage in expanded) {
                                        expanded - group.appPackage
                                    } else {
                                        expanded + group.appPackage
                                    }
                            },
                            onPick = onPick,
                        )
                    }
                }
            }
        }
    }
}

private fun cardShape(index: Int, count: Int): RoundedCornerShape {
    val r = 20.dp
    val c = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(
            topStart = r, topEnd = r, bottomStart = c, bottomEnd = c,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = c, topEnd = c, bottomStart = r, bottomEnd = r,
        )
        else -> RoundedCornerShape(c)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetGroupCard(
    group: WidgetGroup,
    catalog: WidgetCatalog,
    isOpen: Boolean,
    cardBg: Color,
    primary: Color,
    secondary: Color,
    shape: RoundedCornerShape,
    onToggle: () -> Unit,
    onPick: (WidgetOption) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = catalog.appIcons[group.appPackage]
            if (icon != null) {
                Image(
                    bitmap = remember(icon) {
                        icon.toBitmap(160, 160).asImageBitmap()
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(group.appLabel, color = primary, fontSize = 16.sp)
                Text(
                    pluralStringResource(
                        R.plurals.widget_count, group.widgets.size,
                        group.widgets.size,
                    ),
                    color = secondary, fontSize = 13.sp,
                )
            }
            val rot by animateFloatAsState(if (isOpen) 180f else 0f)
            Icon(
                Icons.Filled.ExpandMore, contentDescription = null,
                tint = secondary,
                modifier = Modifier.graphicsLayer { rotationZ = rot },
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    16.dp, Alignment.CenterHorizontally,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                group.widgets.forEach { option ->
                    WidgetPreviewItem(
                        option = option,
                        info = catalog.infos[option.provider],
                        primary = primary,
                        secondary = secondary,
                        onPick = { onPick(option) },
                    )
                }
            }
        }
    }
}

/** One preview + label + "2 × 2" + description, tappable to add. */
@Composable
private fun WidgetPreviewItem(
    option: WidgetOption,
    info: AppWidgetProviderInfo?,
    primary: Color,
    secondary: Color,
    onPick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val cellWPx = with(density) {
        ((resources.configuration.screenWidthDp - 2 * GRID_PAD_DP -
            (COLUMNS - 1) * GRID_GAP_DP) / COLUMNS).dp.toPx()
    }
    val cellHPx = with(density) { CELL_HEIGHT_DP.dp.toPx() }
    val gapPx = with(density) { GRID_GAP_DP.dp.toPx() }
    val span = option.span
    val fullW = (span.cols * cellWPx + (span.cols - 1) * gapPx).toInt()
    val fullH = (span.rows * cellHPx + (span.rows - 1) * gapPx).toInt()
    val maxWPx = with(density) {
        (resources.configuration.screenWidthDp - 64).dp.toPx()
    }
    val maxHPx = with(density) { 220.dp.toPx() }
    val scale = fitPreview(fullW, fullH, maxWPx, maxHPx)
    val scaledW = (fullW * scale).toInt().coerceAtLeast(1)
    val scaledH = (fullH * scale).toInt().coerceAtLeast(1)
    val scaledWDp = with(density) { scaledW.toDp() }
    val scaledHDp = with(density) { scaledH.toDp() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 120.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(scaledWDp, scaledHDp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopStart,
        ) {
            WidgetPreview(info, option.appPackage, fullW, fullH, scale)
            // Eats touches so RemoteViews previews can't steal the pick tap.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPick,
                    ),
            )
        }
        Text(
            option.label, color = primary, fontSize = 14.sp,
            fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${span.cols} \u00D7 ${span.rows}",
            color = secondary, fontSize = 13.sp,
        )
        option.description?.let {
            Text(
                it, color = secondary, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** RemoteViews/generated preview -> previewLayout -> preview image -> icon. */
@Composable
private fun WidgetPreview(
    info: AppWidgetProviderInfo?,
    pkg: String,
    fullW: Int,
    fullH: Int,
    scale: Float,
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    if (info == null) {
        PreviewFallback(pkg, fullW, fullH, scale)
        return
    }
    val views = remember(info) {
        runCatching {
            when {
                Build.VERSION.SDK_INT >= 35 &&
                    info.generatedPreviewCategories and
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 -> {
                    AppWidgetManager.getInstance(context)
                        .getWidgetPreview(
                            info.provider, Process.myUserHandle(),
                            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        )
                }
                Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0 ->
                    RemoteViews(info.provider.packageName, info.previewLayout)
                else -> null
            }
        }.getOrNull()
    }
    if (views != null) {
        AndroidView(
            factory = { ctx ->
                runCatching {
                    AppWidgetHostView(ctx).apply {
                        setAppWidget(-1, info)
                        updateAppWidget(views)
                    }
                }.getOrElse { android.view.View(ctx) }
            },
            modifier = Modifier
                .size(with(LocalDensity.current) {
                    androidx.compose.ui.unit.DpSize(
                        fullW.toDp(), fullH.toDp(),
                    )
                })
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        )
        return
    }
    val preview by produceState<Drawable?>(null, info) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                info.loadPreviewImage(
                    context, context.resources.displayMetrics.densityDpi,
                )
            }.getOrNull()
        }
    }
    if (preview != null) {
        Image(
            bitmap = remember(preview) {
                // Rasterize at the drawable's own aspect; ContentScale.Fit
                // centers it in the cell-shaped box.
                val cap = maxOf(fullW, fullH)
                var w = preview!!.intrinsicWidth
                var h = preview!!.intrinsicHeight
                if (w <= 0 || h <= 0) {
                    w = fullW
                    h = fullH
                } else {
                    val s = minOf(1f, cap.toFloat() / maxOf(w, h))
                    w = (w * s).toInt().coerceAtLeast(1)
                    h = (h * s).toInt().coerceAtLeast(1)
                }
                preview!!.toBitmap(w, h).asImageBitmap()
            },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        PreviewFallback(pkg, fullW, fullH, scale)
    }
}

/** App icon centered in a translucent rounded box of the preview's size. */
@Composable
private fun PreviewFallback(pkg: String, fullW: Int, fullH: Int, scale: Float) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val density = LocalDensity.current
    val icon = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg) }
            .getOrNull()?.toBitmap(160, 160)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let {
            Image(bitmap = it, contentDescription = null,
                modifier = Modifier.size(48.dp))
        }
    }
}
