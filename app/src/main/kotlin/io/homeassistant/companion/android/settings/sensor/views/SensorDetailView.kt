package io.homeassistant.companion.android.settings.sensor.views

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.contentColorFor
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.core.net.toUri
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.sensors.HealthConnectSensorManager
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.compose.TransparentChip
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun SensorDetailView(
    viewModel: SensorDetailViewModel,
    onSetEnabled: (Boolean, Int?) -> Unit,
    onToggleSettingSubmitted: (SensorSetting) -> Unit,
    onDialogSettingClicked: (SensorSetting) -> Unit,
    onDialogSettingSubmitted: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit,
) {
    val context = LocalContext.current
    var sensorUpdateTypeInfo by remember { mutableStateOf(false) }

    var sensorEnabled by remember { mutableStateOf(false) }
    val showPrivacyHint by viewModel.showPrivacyHint.collectAsState()

    LaunchedEffect(Unit) {
        sensorEnabled = viewModel.sensor?.sensor?.enabled
            ?: (
                viewModel.basicSensor != null &&
                    viewModel.basicSensor.enabledByDefault &&
                    viewModel.sensorManager?.checkPermission(context, viewModel.basicSensor.id) == true
                )
    }

    val scaffoldState = rememberScaffoldState()
    LaunchedEffect("snackbar") {
        viewModel.permissionSnackbar.onEach {
            scaffoldState.snackbarHostState.showSnackbar(
                context.getString(it.message),
                context.getString(commonR.string.settings),
            ).let { result ->
                if (result == SnackbarResult.ActionPerformed) {
                    if (it.actionOpensSettings) {
                        if (viewModel.sensorId.startsWith("health_connect")) {
                            HealthConnectSensorManager.getPermissionIntent()?.let { intent ->
                                context.startActivity(intent)
                            }
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        }
                    } else {
                        onSetEnabled(true, it.serverId)
                    }
                }
            }
        }.launchIn(this)
    }

    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = {
            SnackbarHost(
                hostState = scaffoldState.snackbarHostState,
                modifier = Modifier.windowInsetsPadding(safeBottomWindowInsets(applyHorizontal = false)),
            )
        },
    ) { contentPadding ->
        if (sensorUpdateTypeInfo && viewModel.basicSensor != null) {
            SensorDetailUpdateInfoDialog(
                basicSensor = viewModel.basicSensor,
                sensorEnabled = sensorEnabled,
                userSetting = viewModel.settingUpdateFrequency,
                onDismiss = { sensorUpdateTypeInfo = false },
            )
        } else {
            viewModel.sensorSettingsDialog?.let {
                SensorDetailSettingDialog(
                    viewModel = viewModel,
                    state = it,
                    onDismiss = { viewModel.cancelSettingWithDialog() },
                    onSubmit = { state -> onDialogSettingSubmitted(state) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = safeBottomPaddingValues(applyHorizontal = false),
        ) {
            if (viewModel.sensorManager != null && viewModel.basicSensor != null) {
                item {
                    SensorDetailTopPanel(
                        basicSensor = viewModel.basicSensor,
                        dbSensor = viewModel.sensors,
                        sensorsExpanded = viewModel.serversStateExpand.value,
                        serverNames = viewModel.serverNames,
                        onSetEnabled = onSetEnabled,
                    )
                }
                item {
                    Text(
                        text = stringResource(viewModel.basicSensor.descriptionId),
                        modifier = Modifier.padding(all = 16.dp),
                    )
                }
                if (showPrivacyHint) {
                    item {
                        // Display privacy hint for Google Play Store compliance. While required for Health
                        // sensors, we show this hint for all sensors as the privacy information is universally
                        // relevant and helps users understand how their sensor data is used.
                        HAHint(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            text = stringResource(commonR.string.sensor_privacy),
                            onClose = viewModel::discardShowPrivacyHint,
                        )
                    }
                }
                item {
                    TransparentChip(
                        modifier = Modifier.padding(start = 16.dp, bottom = 40.dp),
                        text = stringResource(
                            when (viewModel.basicSensor.updateType) {
                                SensorManager.BasicSensor.UpdateType.INTENT ->
                                    commonR.string.sensor_update_type_chip_intent

                                SensorManager.BasicSensor.UpdateType.INTENT_ONLY ->
                                    commonR.string.sensor_update_type_chip_intent_only

                                SensorManager.BasicSensor.UpdateType.WORKER -> {
                                    when (viewModel.settingUpdateFrequency) {
                                        SensorUpdateFrequencySetting.FAST_ALWAYS ->
                                            commonR.string.sensor_update_type_chip_worker_fast_always

                                        SensorUpdateFrequencySetting.FAST_WHILE_CHARGING ->
                                            commonR.string.sensor_update_type_chip_worker_fast_charging

                                        SensorUpdateFrequencySetting.NORMAL ->
                                            commonR.string.sensor_update_type_chip_worker_normal
                                    }
                                }

                                SensorManager.BasicSensor.UpdateType.LOCATION ->
                                    commonR.string.sensor_update_type_chip_location

                                SensorManager.BasicSensor.UpdateType.CUSTOM ->
                                    commonR.string.sensor_update_type_chip_custom
                            },
                        ),
                        icon = CommunityMaterial.Icon.cmd_clock_fast,
                    ) {
                        sensorUpdateTypeInfo = true
                    }
                }
                viewModel.sensor?.let { sensor ->
                    if (sensor.sensor.enabled && sensor.attributes.isNotEmpty()) {
                        item {
                            SettingsSubheader(stringResource(commonR.string.attributes))
                        }
                        items(sensor.attributes, key = { "${it.sensorId}-${it.name}" }) { attribute ->
                            val summary = when (attribute.valueType) {
                                "listboolean" -> kotlinJsonMapper.decodeFromString<List<Boolean>>(
                                    attribute.value,
                                ).toString()

                                "listfloat" -> kotlinJsonMapper.decodeFromString<List<Float>>(
                                    attribute.value,
                                ).toString()

                                "listlong" ->
                                    kotlinJsonMapper.decodeFromString<List<Long>>(attribute.value).toString()

                                "listint" ->
                                    kotlinJsonMapper.decodeFromString<List<Int>>(attribute.value).toString()

                                "liststring" -> kotlinJsonMapper.decodeFromString<List<String>>(
                                    attribute.value,
                                ).toString()

                                else -> attribute.value
                            }
                            SensorDetailRow(
                                title = attribute.name,
                                summary = summary,
                                clickable = false,
                                selectingEnabled = true,
                            )
                        }
                    }
                    if (sensor.sensor.enabled && viewModel.sensorSettings.value.isNotEmpty()) {
                        item {
                            SettingsSubheader(stringResource(commonR.string.sensor_settings))
                        }
                        items(viewModel.sensorSettings.value, key = { "${it.sensorId}-${it.name}" }) { setting ->
                            when (setting.valueType) {
                                SensorSettingType.TOGGLE -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        switch = setting.value == "true",
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { isEnabled ->
                                            onToggleSettingSubmitted(
                                                SensorSetting(
                                                    viewModel.basicSensor.id,
                                                    setting.name,
                                                    isEnabled.toString(),
                                                    SensorSettingType.TOGGLE,
                                                    setting.enabled,
                                                ),
                                            )
                                        },
                                    )
                                }

                                SensorSettingType.LIST,
                                SensorSettingType.LIST_APPS,
                                SensorSettingType.LIST_BLUETOOTH,
                                SensorSettingType.LIST_ZONES,
                                SensorSettingType.LIST_BEACONS,
                                -> {
                                    val summaryValues = setting.value.split(", ")
                                        .mapNotNull { it.ifBlank { null } }
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary =
                                        if (summaryValues.any()) {
                                            viewModel.getSettingEntries(setting, summaryValues)
                                                .joinToString(", ")
                                        } else {
                                            stringResource(commonR.string.none_selected)
                                        },
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) },
                                    )
                                }

                                SensorSettingType.STRING, SensorSettingType.NUMBER -> {
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = setting.value,
                                        enabled = setting.enabled,
                                        clickable = setting.enabled,
                                        onClick = { onDialogSettingClicked(setting) },
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
    dbSensor: List<SensorWithAttributes>,
    sensorsExpanded: Boolean,
    serverNames: Map<Int, String>,
    onSetEnabled: (Boolean, Int?) -> Unit,
) {
    val context = LocalContext.current
    val sensor = dbSensor.map { it.sensor }.maxByOrNull { it.enabled }

    Surface(color = colorResource(commonR.color.colorSensorTopBackground)) {
        Column {
            CompositionLocalProvider(
                LocalContentAlpha provides (if (sensor?.enabled == true) ContentAlpha.high else ContentAlpha.disabled),
            ) {
                val cardElevation: Dp by animateDpAsState(if (sensor?.enabled == true) 8.dp else 1.dp)
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                    elevation = cardElevation,
                ) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colors.background)
                            .padding(all = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var iconToUse = basicSensor.statelessIcon
                        if (sensor?.enabled == true && sensor.icon.isNotBlank()) {
                            iconToUse = sensor.icon
                        }
                        val mdiIcon = try {
                            IconicsDrawable(context, "cmd-${iconToUse.split(":")[1]}").icon
                        } catch (e: Exception) {
                            null
                        }

                        if (mdiIcon != null) {
                            Image(
                                asset = mdiIcon,
                                contentDescription = stringResource(commonR.string.icon),
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(if (sensor?.enabled == true) ContentAlpha.high else ContentAlpha.disabled),
                                colorFilter = ColorFilter.tint(
                                    if (sensor?.enabled == true) {
                                        colorResource(commonR.color.colorSensorIconEnabled)
                                    } else {
                                        contentColorFor(backgroundColor = MaterialTheme.colors.background)
                                    },
                                ),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = stringResource(basicSensor.name),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .weight(0.5f),
                        )
                        SelectionContainer(modifier = Modifier.weight(0.5f)) {
                            Text(
                                text = if (sensor?.enabled == true) {
                                    if (sensor.state.isBlank()) {
                                        stringResource(commonR.string.enabled)
                                    } else {
                                        if (sensor.unitOfMeasurement.isNullOrBlank() ||
                                            sensor.state.toDoubleOrNull() == null
                                        ) {
                                            sensor.state
                                        } else {
                                            "${sensor.state} ${sensor.unitOfMeasurement}"
                                        }
                                    }
                                } else {
                                    stringResource(commonR.string.disabled)
                                },
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.animateContentSize()) {
                if (sensorsExpanded) {
                    dbSensor.forEach { thisSensor ->
                        SensorDetailEnableRow(
                            basicSensor = basicSensor,
                            enabled = thisSensor.sensor.enabled,
                            serverName = serverNames[thisSensor.sensor.serverId],
                            onSetEnabled = { onSetEnabled(!thisSensor.sensor.enabled, thisSensor.sensor.serverId) },
                        )
                    }
                } else {
                    SensorDetailEnableRow(
                        basicSensor = basicSensor,
                        enabled = sensor?.enabled == true,
                        serverName = null,
                        onSetEnabled = { onSetEnabled(sensor?.enabled != true, null) },
                    )
                }
            }
            Divider()
        }
    }
}

@Composable
fun SensorDetailEnableRow(
    basicSensor: SensorManager.BasicSensor,
    enabled: Boolean,
    serverName: String?,
    onSetEnabled: () -> Unit,
) {
    val enableBarModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .clickable { onSetEnabled() }
    val switchDescription = stringResource(
        if (basicSensor.type == "binary_sensor" || basicSensor.type == "sensor") {
            commonR.string.enable_sensor
        } else {
            (if (enabled) commonR.string.enabled else commonR.string.disabled)
        },
    )
    Box(
        modifier =
        if (enabled) {
            Modifier
                .background(colorResource(commonR.color.colorSensorTopEnabled))
                .then(enableBarModifier)
        } else {
            enableBarModifier
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(all = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (serverName.isNullOrBlank()) switchDescription else "$serverName: $switchDescription",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 16.dp),
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                ),
            )
        }
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
    onClick: (Boolean?) -> Unit = { },
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentAlpha provides (if (enabled) ContentAlpha.high else ContentAlpha.disabled),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = title, style = MaterialTheme.typography.body1)
                if (summary != null) {
                    CompositionLocalProvider(
                        LocalContentAlpha provides (if (enabled) ContentAlpha.medium else ContentAlpha.disabled),
                    ) {
                        if (selectingEnabled) {
                            SelectionContainer { Text(text = summary, style = MaterialTheme.typography.body2) }
                        } else {
                            Text(text = summary, style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }
            if (switch != null) {
                Switch(
                    checked = switch,
                    onCheckedChange = null,
                    enabled = clickable,
                    modifier = Modifier.padding(start = 16.dp),
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                    ),
                )
            }
        }
    }
}

@Composable
fun SensorDetailSettingDialog(
    viewModel: SensorDetailViewModel,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSubmit: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val listSettingDialog = state.setting.valueType.listType
    val inputValue = remember(state.loading) { mutableStateOf(state.setting.value) }
    val checkedValue =
        remember(state.loading) { mutableStateListOf<String>().also { it.addAll(state.entriesSelected) } }

    MdcAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.getSettingTranslatedTitle(state.setting.name)) },
        content = {
            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (listSettingDialog) {
                LazyColumn {
                    items(state.entries, key = { (id) -> id }) { (id, entry) ->
                        SensorDetailSettingRow(
                            label = entry,
                            checked = if (state.setting.valueType ==
                                SensorSettingType.LIST
                            ) {
                                inputValue.value == id
                            } else {
                                checkedValue.contains(id)
                            },
                            multiple = state.setting.valueType != SensorSettingType.LIST,
                            onClick = { isChecked ->
                                if (state.setting.valueType == SensorSettingType.LIST) {
                                    inputValue.value = id
                                    onSubmit(state.copy().apply { setting.value = inputValue.value })
                                } else {
                                    if (checkedValue.contains(id) && !isChecked) {
                                        checkedValue.remove(id)
                                    } else if (!checkedValue.contains(id) && isChecked) {
                                        checkedValue.add(id)
                                    }
                                }
                            },
                        )
                    }
                }
            } else {
                TextField(
                    value = inputValue.value,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (state.setting.valueType == SensorSettingType.NUMBER) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Text
                        },
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() },
                    ),
                    onValueChange = { input -> inputValue.value = input },
                )
            }
        },
        onCancel = onDismiss,
        onSave = if (state.loading) {
            null
        } else if (state.setting.valueType != SensorSettingType.LIST) {
            {
                if (listSettingDialog) {
                    inputValue.value = checkedValue.joinToString().replace("[", "").replace("]", "")
                }
                onSubmit(state.copy().apply { setting.value = inputValue.value })
            }
        } else { // list is saved when selecting a value
            null
        },
        contentPadding = if (listSettingDialog) PaddingValues(all = 0.dp) else PaddingValues(horizontal = 24.dp),
    )
}

@Composable
fun SensorDetailUpdateInfoDialog(
    basicSensor: SensorManager.BasicSensor,
    sensorEnabled: Boolean,
    userSetting: SensorUpdateFrequencySetting,
    onDismiss: () -> Unit,
) {
    MdcAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(commonR.string.sensor_update_type_info_title)) },
        content = {
            var infoString = when (basicSensor.updateType) {
                SensorManager.BasicSensor.UpdateType.INTENT -> stringResource(
                    commonR.string.sensor_update_type_info_intent,
                )

                SensorManager.BasicSensor.UpdateType.INTENT_ONLY -> {
                    "${
                        stringResource(
                            commonR.string.sensor_update_type_info_intent,
                        )
                    }\n\n${stringResource(commonR.string.sensor_update_type_info_intent_only)}"
                }

                SensorManager.BasicSensor.UpdateType.WORKER -> {
                    "${
                        stringResource(
                            when (userSetting) {
                                SensorUpdateFrequencySetting.FAST_ALWAYS ->
                                    commonR.string.sensor_update_type_info_worker_fast_always
                                SensorUpdateFrequencySetting.FAST_WHILE_CHARGING ->
                                    commonR.string.sensor_update_type_info_worker_fast_charging
                                SensorUpdateFrequencySetting.NORMAL ->
                                    commonR.string.sensor_update_type_info_worker_normal
                            },
                        )
                    }\n\n${
                        stringResource(
                            commonR.string.sensor_update_type_info_worker_setting,
                            stringResource(commonR.string.sensor_update_frequency),
                        )
                    }"
                }

                SensorManager.BasicSensor.UpdateType.LOCATION -> stringResource(
                    commonR.string.sensor_update_type_info_location,
                )

                SensorManager.BasicSensor.UpdateType.CUSTOM -> stringResource(
                    commonR.string.sensor_update_type_info_custom,
                )
            }
            if (!sensorEnabled && (basicSensor.type == "binary_sensor" || basicSensor.type == "sensor")) {
                infoString = stringResource(commonR.string.sensor_update_type_info_enable) + infoString
            }

            Text(infoString)
        },
        onOK = onDismiss,
    )
}

@Composable
fun SensorDetailSettingRow(label: String, checked: Boolean, multiple: Boolean, onClick: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick(!checked) }
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiple) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
            )
        } else {
            RadioButton(
                selected = checked,
                onClick = null,
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
            )
        }
        Text(label)
    }
}
