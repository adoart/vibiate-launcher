package com.vibiate.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.HOME_SCREEN_ID
import com.vibiate.launcher.data.SettingsRepository
import com.vibiate.launcher.ui.theme.LocalAviateColors
import com.vibiate.launcher.widget.WidgetController

/**
 * Home is the widget grid for [HOME_SCREEN_ID]; Google's own widgets (clock,
 * At a glance, search) are seeded onto it by MainActivity on first run.
 */
@Composable
fun HomeScreen(
    settings: SettingsRepository,
    controller: WidgetController,
    editBlur: Float = 0f,
    onEditingChange: (Boolean) -> Unit = {},
) {
    val homeWidgets by controller.widgets.collectAsState()
    val hasWidgets = homeWidgets.any { it.screenId == HOME_SCREEN_ID }
    val aviate = LocalAviateColors.current

    Box(Modifier.fillMaxSize()) {
        WidgetsScreen(
            controller = controller,
            screenId = HOME_SCREEN_ID,
            header = { Spacer(Modifier.height(24.dp)) },
            showEmptyState = false,
            editBlur = editBlur,
            onEditingChange = onEditingChange,
        )
        if (!hasWidgets) {
            Text(
                text = stringResource(R.string.swipe_hint),
                fontSize = 12.sp,
                color = aviate.homeTextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            )
        }
    }
}
