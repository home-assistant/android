package io.homeassistant.companion.android.settings.sensor.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
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

    viewModel.sensorSettingsDialog.value?.let {
        SensorDetailSettingDialog(
            viewModel = viewModel,
            state = it,
            onDismiss = { viewModel.cancelSettingWithDialog() },
            onSubmit = { state -> onDialogSettingSubmitted(state) }
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (viewModel.sensorManager != null &&
            viewModel.basicSensor != null
        ) {
            item {
                SensorDetailRow(
                    title = stringResource(commonR.string.enabled),
                    summary = stringResource(commonR.string.enabled_summary),
                    switch = viewModel.sensor.value?.sensor?.enabled
                        ?: (viewModel.sensorManager.enabledByDefault && viewModel.sensorManager.checkPermission(context, viewModel.basicSensor.id)),
                    onClick = { onSetEnabled(it!!) }
                )
            }
            item {
                SensorDetailRow(
                    title = stringResource(commonR.string.sensor_description),
                    summary = stringResource(viewModel.basicSensor.descriptionId),
                    clickable = false
                )
            }
            viewModel.sensor.value?.let { sensor ->
                item {
                    SensorDetailRow(
                        title = stringResource(commonR.string.unique_id),
                        summary = viewModel.basicSensor.id,
                        clickable = false
                    )
                }
                item {
                    SensorDetailRow(
                        title = stringResource(commonR.string.state),
                        summary = if (!sensor.sensor.enabled) {
                            stringResource(commonR.string.disabled)
                        } else {
                            if (sensor.sensor.unitOfMeasurement.isNullOrBlank()) sensor.sensor.state
                            else "${sensor.sensor.state} ${sensor.sensor.unitOfMeasurement}"
                        },
                        clickable = false
                    )
                }
                if (sensor.sensor.deviceClass != null) {
                    item {
                        SensorDetailRow(
                            title = stringResource(commonR.string.device_class),
                            summary = sensor.sensor.deviceClass!!,
                            clickable = false
                        )
                    }
                }
                if (sensor.sensor.icon.isNotBlank()) {
                    item {
                        SensorDetailRow(
                            title = stringResource(commonR.string.icon),
                            summary = sensor.sensor.icon,
                            clickable = false
                        )
                    }
                }
                if (sensor.sensor.enabled && sensor.attributes.isNotEmpty()) {
                    item {
                        SensorDetailHeader(stringResource(commonR.string.attributes))
                    }
                    sensor.attributes.forEach { attribute ->
                        item {
                            SensorDetailRow(
                                title = attribute.name,
                                summary = attribute.value,
                                clickable = false
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
                                "toggle " -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        switch = setting.value == "true",
                                        clickable = setting.enabled,
                                        onClick = { isEnabled ->
                                            onToggleSettingSubmitted(
                                                SensorSetting(viewModel.basicSensor.id, setting.name, isEnabled.toString(), "toggle", setting.enabled)
                                            )
                                        }
                                    )
                                }
                                "list", "list-apps", "list-bluetooth", "list-zones" -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = setting.value.split(", ").map { it }.toString(),
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) }
                                    )
                                }
                                "string", "number" -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = setting.value,
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
    clickable: Boolean = true,
    onClick: (Boolean?) -> Unit = { }
) {
    var rowModifier = Modifier
        .heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp)
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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            if (summary != null) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
        if (switch != null) {
            Switch(
                checked = switch,
                onCheckedChange = { onClick(!switch) },
                enabled = clickable
            )
        }
    }
}

@Composable
fun SensorDetailSettingDialog(
    viewModel: SensorDetailViewModel,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSubmit: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit
) {
    var inputValue by remember { mutableStateOf(state.setting.value) }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colors.surface, RoundedCornerShape(4.dp))) {
            LazyColumn {
                item {
                    Text(
                        text = viewModel.getSettingTranslatedTitle(state.setting.name),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.h6
                    )
                }
                item {
                    if (state.setting.valueType == "string" || state.setting.valueType == "number") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            OutlinedTextField(
                                value = inputValue,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (state.setting.valueType == "number") {
                                        KeyboardType.Number
                                    } else {
                                        KeyboardType.Text
                                    }
                                ),
                                onValueChange = { input -> inputValue = input.trim() }
                            )
                        }
                    } else { // List of entries
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .heightIn(max = 300.dp)
                        ) {
                        }
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp, bottom = 8.dp)
                    ) {
                        TextButton(modifier = Modifier.padding(end = 8.dp), onClick = onDismiss) {
                            Text(stringResource(commonR.string.cancel))
                        }
                        TextButton(onClick = { onSubmit(state.copy().apply { setting.value = inputValue }) }) {
                            Text(stringResource(commonR.string.save))
                        }
                    }
                }
            }
        }
    }
}
