package com.vibiate.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibiate.launcher.R
import com.vibiate.launcher.data.IconPackInfo
import com.vibiate.launcher.data.IconPackManager
import com.vibiate.launcher.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single radio row used by picker dialogs (grid size, font, icon pack, interval). */
@Composable
fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Icon-pack picker: "System default" plus every discovered pack. Shared by the
 * customize sheet and the settings screen.
 */
@Composable
fun IconPackPickerDialog(
    settings: SettingsRepository,
    iconPackManager: IconPackManager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val iconPackPkg by settings.iconPackPackage.collectAsState(initial = "")
    val iconPacks by produceState<List<IconPackInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) { iconPackManager.installedPacks() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customize_icon_pack)) },
        text = {
            Column {
                RadioOption(
                    label = stringResource(R.string.icon_pack_default),
                    selected = iconPackPkg.isEmpty(),
                ) {
                    scope.launch { settings.setIconPack("") }
                    onDismiss()
                }
                iconPacks.forEach { pack ->
                    RadioOption(
                        label = pack.label,
                        selected = iconPackPkg == pack.packageName,
                    ) {
                        scope.launch { settings.setIconPack(pack.packageName) }
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
