package io.homeassistant.companion.android.util.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import androidx.compose.material3.DropdownMenuItem as DropdownMenuItemM3
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox as ExposedDropdownMenuBoxM3
import androidx.compose.material3.ExposedDropdownMenuDefaults as ExposedDropdownMenuDefaultsM3
import androidx.compose.material3.MenuAnchorType as MenuAnchorTypeM3
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.TextField as TextFieldM3
import androidx.compose.material3.Text as TextM3
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ExposedDropdownMenu(
    label: String,
    keys: List<String>,
    currentIndex: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextField(
            readOnly = true,
            value = currentIndex?.let { keys[it] } ?: "",
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            keys.forEachIndexed { index, key ->
                DropdownMenuItem(onClick = {
                    onSelected(index)
                    expanded = false
                    focusManager.clearFocus()
                }) {
                    Text(key)
                }
            }
        }
    }
}

@Composable
fun ServerExposedDropdownMenu(
    servers: List<Server>,
    current: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = commonR.string.server_select,
) {
    val keys = servers.map { it.friendlyName }
    val ids = servers.map { it.id }
    val currentIndex = servers.indexOfFirst { it.id == current }.takeUnless { it == -1 }
    HAExposedDropdownMenu(
        label = stringResource(title),
        keys = keys,
        currentIndex = currentIndex,
        onSelected = { onSelected(ids[it]) },
        modifier = modifier,
    )
}

@Composable
fun WidgetBackgroundTypeExposedDropdownMenu(
    current: WidgetBackgroundType?,
    onSelected: (WidgetBackgroundType) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = commonR.string.widget_background_type_title,
) {
    val context = LocalContext.current
    val keys = remember { WidgetUtils.getBackgroundOptionList(context) }
    val currentIndex =
        remember(current) { current?.let { WidgetUtils.getSelectedBackgroundOption(context, current, keys) } }
    HAExposedDropdownMenu(
        label = stringResource(title),
        keys = keys.toList(),
        currentIndex = currentIndex,
        onSelected = { onSelected(WidgetUtils.getWidgetBackgroundType(context, keys[it])) },
        modifier = modifier,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAExposedDropdownMenu(
    label: String,
    keys: List<String>,
    currentIndex: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val colorScheme = LocalHAColorScheme.current
    ExposedDropdownMenuBoxM3(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextFieldM3(
            readOnly = true,
            value = currentIndex?.let { keys[it] } ?: "",
            onValueChange = { },
            label = { TextM3(label) },
            trailingIcon = { ExposedDropdownMenuDefaultsM3.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaultsM3.textFieldColors(
                unfocusedContainerColor = colorScheme.colorSurfaceDefault,
                focusedContainerColor = colorScheme.colorSurfaceDefault,
                unfocusedLabelColor = colorScheme.colorTextSecondary,
                focusedLabelColor = colorScheme.colorOnPrimaryNormal,
                unfocusedTrailingIconColor = colorScheme.colorTextSecondary,
                focusedTrailingIconColor = colorScheme.colorOnPrimaryNormal,
                unfocusedTextColor = colorScheme.colorTextPrimary,
                focusedTextColor = colorScheme.colorTextPrimary,
                focusedIndicatorColor = colorScheme.colorOnPrimaryNormal,
                unfocusedIndicatorColor = colorScheme.colorBorderNeutralQuiet,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorTypeM3.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colorScheme.colorSurfaceDefault
            ) {
            keys.forEachIndexed { index, key ->
                DropdownMenuItemM3(
                    text = { TextM3(key) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                        focusManager.clearFocus()
                    },
                    colors = MenuItemColors(
                        textColor = colorScheme.colorTextPrimary,
                        leadingIconColor = colorScheme.colorOnPrimaryNormal,
                        trailingIconColor = colorScheme.colorOnPrimaryNormal,
                        disabledTextColor = colorScheme.colorTextSecondary,
                        disabledLeadingIconColor = colorScheme.colorTextDisabled,
                        disabledTrailingIconColor = colorScheme.colorTextDisabled,
                    )
                )
            }
        }
    }
}
