package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.compose.TransparentChip
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SensorDetailView(
    viewModel: SensorDetailViewModel,
    onSetEnabled: (Boolean) -> Unit,
    onToggleSettingSubmitted: (SensorSetting) -> Unit,
    onDialogSettingClicked: (SensorSetting) -> Unit,
    onDialogSettingSubmitted: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit
) {
    val context = LocalContext.current
    var sensorUpdateTypeInfo by remember { mutableStateOf(false) }

    val sensorEnabled = viewModel.sensor.value?.sensor?.enabled
        ?: (
            viewModel.basicSensor != null && viewModel.sensorManager?.enabledByDefault == true &&
                viewModel.sensorManager.checkPermission(context, viewModel.basicSensor.id)
            )

    if (sensorUpdateTypeInfo && viewModel.basicSensor != null) {
        SensorDetailUpdateInfoDialog(
            basicSensor = viewModel.basicSensor,
            sensorEnabled = sensorEnabled,
            userSetting = viewModel.settingUpdateFrequency,
            onDismiss = { sensorUpdateTypeInfo = false }
        )
    } else viewModel.sensorSettingsDialog.value?.let {
        SensorDetailSettingDialog(
            viewModel = viewModel,
            state = it,
            onDismiss = { viewModel.cancelSettingWithDialog() },
            onSubmit = { state -> onDialogSettingSubmitted(state) }
        )
    }
    LazyColumn {
        if (viewModel.sensorManager != null && viewModel.basicSensor != null) {
            item {
                SensorDetailTopPanel(
                    basicSensor = viewModel.basicSensor,
                    dbSensor = viewModel.sensor.value?.sensor,
                    sensorEnabled = sensorEnabled,
                    onSetEnabled = onSetEnabled
                )
            }
            item {
                Text(
                    text = stringResource(viewModel.basicSensor.descriptionId),
                    modifier = Modifier.padding(all = 16.dp)
                )
            }
            item {
                TransparentChip(
                    modifier = Modifier.padding(start = 16.dp, bottom = 40.dp),
                    text = stringResource(
                        when (viewModel.basicSensor.updateType) {
                            SensorManager.BasicSensor.UpdateType.INTENT -> commonR.string.sensor_update_type_chip_intent
                            SensorManager.BasicSensor.UpdateType.WORKER -> {
                                when (viewModel.settingUpdateFrequency) {
                                    SensorUpdateFrequencySetting.FAST_ALWAYS -> commonR.string.sensor_update_type_chip_worker_fast_always
                                    SensorUpdateFrequencySetting.FAST_WHILE_CHARGING -> commonR.string.sensor_update_type_chip_worker_fast_charging
                                    SensorUpdateFrequencySetting.NORMAL -> commonR.string.sensor_update_type_chip_worker_normal
                                }
                            }
                            SensorManager.BasicSensor.UpdateType.LOCATION -> commonR.string.sensor_update_type_chip_location
                            SensorManager.BasicSensor.UpdateType.CUSTOM -> commonR.string.sensor_update_type_chip_custom
                        }
                    ),
                    icon = CommunityMaterial.Icon.cmd_clock_fast
                ) {
                    sensorUpdateTypeInfo = true
                }
            }
            viewModel.sensor.value?.let { sensor ->
                if (sensor.sensor.enabled && sensor.attributes.isNotEmpty()) {
                    item {
                        SensorDetailHeader(stringResource(commonR.string.attributes))
                    }
                    sensor.attributes.forEach { attribute ->
                        item {
                            SensorDetailRow(
                                title = attribute.name,
                                summary = attribute.value,
                                clickable = false,
                                selectingEnabled = true
                            )
                        }
                    }
                }
                if (sensor.sensor.enabled && viewModel.sensorSettings.isNotEmpty()) {
                    item {
                        SensorDetailHeader(stringResource(commonR.string.sensor_settings))
                    }
                    viewModel.sensorSettings.forEach { setting ->
                        item {
                            when (setting.valueType) {
                                "toggle" -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        switch = setting.value == "true",
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { isEnabled ->
                                            onToggleSettingSubmitted(
                                                SensorSetting(viewModel.basicSensor.id, setting.name, isEnabled.toString(), "toggle", setting.enabled)
                                            )
                                        }
                                    )
                                }
                                "list" -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = viewModel.getSettingTranslatedEntry(setting.name, setting.value),
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) }
                                    )
                                }
                                "list-apps", "list-bluetooth", "list-zones" -> {
                                    val summaryValues = setting.value.split(", ").mapNotNull { it.ifBlank { null } }
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary =
                                        if (summaryValues.any()) summaryValues.toString()
                                        else stringResource(commonR.string.none_selected),
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) }
                                    )
                                }
                                "string", "number" -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = setting.value,
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorDetailTopPanel(
    basicSensor: SensorManager.BasicSensor,
    dbSensor: Sensor?,
    sensorEnabled: Boolean,
    onSetEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Surface(color = colorResource(commonR.color.colorSensorTopBackground)) {
        Column {
            CompositionLocalProvider(
                LocalContentAlpha provides (if (sensorEnabled) ContentAlpha.high else ContentAlpha.disabled)
            ) {
                val cardElevation: Dp by animateDpAsState(if (sensorEnabled) 8.dp else 1.dp)
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                    elevation = cardElevation
                ) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colors.background)
                            .padding(all = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var iconToUse = basicSensor.statelessIcon
                        dbSensor?.let {
                            if (it.enabled && it.icon.isNotBlank()) {
                                iconToUse = it.icon
                            }
                        }
                        val mdiIcon = try {
                            IconicsDrawable(context, "cmd-${iconToUse.split(":")[1]}").icon
                        } catch (e: Exception) { null }

                        if (mdiIcon != null) {
                            Image(
                                asset = mdiIcon,
                                contentDescription = stringResource(commonR.string.icon),
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(if (sensorEnabled) ContentAlpha.high else ContentAlpha.disabled),
                                colorFilter = ColorFilter.tint(
                                    if (sensorEnabled) colorResource(commonR.color.colorSensorIconEnabled)
                                    else contentColorFor(backgroundColor = MaterialTheme.colors.background)
                                )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = stringResource(basicSensor.name),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .weight(0.5f)
                        )
                        SelectionContainer(modifier = Modifier.weight(0.5f)) {
                            Text(
                                text = if (dbSensor?.enabled == true) {
                                    if (dbSensor.state.isBlank()) {
                                        stringResource(commonR.string.enabled)
                                    } else {
                                        if (dbSensor.unitOfMeasurement.isNullOrBlank()) dbSensor.state
                                        else "${dbSensor.state} ${dbSensor.unitOfMeasurement}"
                                    }
                                } else {
                                    stringResource(commonR.string.disabled)
                                },
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            val enableBarModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable {
                    onSetEnabled(!sensorEnabled)
                }
            Column(
                modifier =
                if (sensorEnabled) Modifier.background(colorResource(commonR.color.colorSensorTopEnabled)).then(enableBarModifier)
                else enableBarModifier
            ) {
                Row(
                    modifier = Modifier
                        .padding(all = 16.dp)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (basicSensor.type == "binary_sensor" || basicSensor.type == "sensor") commonR.string.enable_sensor
                            else (if (sensorEnabled) commonR.string.enabled else commonR.string.disabled)
                        ),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = sensorEnabled,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = 16.dp),
                        colors = SwitchDefaults.colors(uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb))
                    )
                }
            }
            Divider()
        }
    }
}

@Composable
fun SensorDetailHeader(text: String) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
    }
}

@Composable
fun SensorDetailRow(
    title: String,
    summary: String? = null,
    switch: Boolean? = null,
    selectingEnabled: Boolean = false,
    enabled: Boolean = true,
    clickable: Boolean = true,
    onClick: (Boolean?) -> Unit = { }
) {
    var rowModifier = Modifier
        .heightIn(min = if (summary.isNullOrBlank()) 56.dp else 72.dp)
        .padding(all = 16.dp)
        .fillMaxWidth()
    if (clickable) {
        rowModifier = Modifier
            .clickable { onClick(if (switch != null) !switch else null) }
            .then(rowModifier)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentAlpha provides (if (enabled) ContentAlpha.high else ContentAlpha.disabled)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = title, style = MaterialTheme.typography.body1)
                if (summary != null) {
                    CompositionLocalProvider(LocalContentAlpha provides (if (enabled) ContentAlpha.medium else ContentAlpha.disabled)) {
                        if (selectingEnabled) SelectionContainer { Text(text = summary, style = MaterialTheme.typography.body2) }
                        else Text(text = summary, style = MaterialTheme.typography.body2)
                    }
                }
            }
            if (switch != null) {
                Switch(
                    checked = switch,
                    onCheckedChange = null,
                    enabled = clickable,
                    modifier = Modifier.padding(start = 16.dp),
                    colors = SwitchDefaults.colors(uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb))
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SensorDetailSettingDialog(
    viewModel: SensorDetailViewModel,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSubmit: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val listSettingDialog = state.setting.valueType != "string" && state.setting.valueType != "number"
    val inputValue = remember { mutableStateOf(state.setting.value) }
    val checkedValue = remember { mutableStateListOf(*state.entriesSelected?.toTypedArray() ?: emptyArray()) }

    MdcAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.getSettingTranslatedTitle(state.setting.name)) },
        content = {
            if (listSettingDialog) {
                LazyColumn {
                    state.entries?.forEachIndexed { index, entry ->
                        val id = state.entriesIds?.get(index)!!
                        item {
                            SensorDetailSettingRow(
                                label = entry,
                                checked = if (state.setting.valueType == "list") inputValue.value == id else checkedValue.contains(id),
                                multiple = state.setting.valueType != "list",
                                onClick = { isChecked ->
                                    if (state.setting.valueType == "list") {
                                        inputValue.value = id
                                        onSubmit(state.copy().apply { setting.value = inputValue.value })
                                    } else {
                                        if (checkedValue.contains(id) && !isChecked) checkedValue.remove(id)
                                        else if (!checkedValue.contains(id) && isChecked) checkedValue.add(id)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = inputValue.value,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (state.setting.valueType == "number") {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Text
                        }
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    ),
                    onValueChange = { input -> inputValue.value = input }
                )
            }
        },
        onCancel = onDismiss,
        onSave = if (state.setting.valueType != "list") {
            {
                if (listSettingDialog) {
                    inputValue.value = checkedValue.joinToString().replace("[", "").replace("]", "")
                }
                onSubmit(state.copy().apply { setting.value = inputValue.value })
            }
        } else null, // list is saved when selecting a value
        contentPadding = if (listSettingDialog) PaddingValues(all = 0.dp) else PaddingValues(horizontal = 24.dp)
    )
}

@Composable
fun SensorDetailUpdateInfoDialog(
    basicSensor: SensorManager.BasicSensor,
    sensorEnabled: Boolean,
    userSetting: SensorUpdateFrequencySetting,
    onDismiss: () -> Unit
) {
    MdcAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(commonR.string.sensor_update_type_info_title)) },
        content = {
            var infoString = when (basicSensor.updateType) {
                SensorManager.BasicSensor.UpdateType.INTENT -> stringResource(commonR.string.sensor_update_type_info_intent)
                SensorManager.BasicSensor.UpdateType.WORKER -> {
                    "${stringResource(
                        when (userSetting) {
                            SensorUpdateFrequencySetting.FAST_ALWAYS -> commonR.string.sensor_update_type_info_worker_fast_always
                            SensorUpdateFrequencySetting.FAST_WHILE_CHARGING -> commonR.string.sensor_update_type_info_worker_fast_charging
                            SensorUpdateFrequencySetting.NORMAL -> commonR.string.sensor_update_type_info_worker_normal
                        }
                    )}\n\n${stringResource(commonR.string.sensor_update_type_info_worker_setting, stringResource(commonR.string.sensor_update_frequency))}"
                }
                SensorManager.BasicSensor.UpdateType.LOCATION -> stringResource(commonR.string.sensor_update_type_info_location)
                SensorManager.BasicSensor.UpdateType.CUSTOM -> stringResource(commonR.string.sensor_update_type_info_custom)
            }
            if (!sensorEnabled && (basicSensor.type == "binary_sensor" || basicSensor.type == "sensor")) {
                infoString = stringResource(commonR.string.sensor_update_type_info_enable) + infoString
            }

            Text(infoString)
        },
        onOK = onDismiss
    )
}

@Composable
fun SensorDetailSettingRow(
    label: String,
    checked: Boolean,
    multiple: Boolean,
    onClick: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick(!checked) }
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiple) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(width = 48.dp, height = 48.dp)
            )
        } else {
            RadioButton(
                selected = checked,
                onClick = null,
                modifier = Modifier.size(width = 48.dp, height = 48.dp)
            )
        }
        Text(label)
    }
}
