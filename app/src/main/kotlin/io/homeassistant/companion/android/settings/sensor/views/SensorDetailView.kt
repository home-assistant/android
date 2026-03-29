package io.homeassistant.companion.android.settings.sensor.views

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
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
import io.homeassistant.companion.android.util.compose.safeScreenHeight
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
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
            viewModel.sensorSettingsDialog?.let { dialogState ->
                val isMultiSelectList = dialogState.setting.valueType in listOf(
                    SensorSettingType.LIST_APPS,
                    SensorSettingType.LIST_BLUETOOTH,
                    SensorSettingType.LIST_ZONES,
                    SensorSettingType.LIST_BEACONS,
                )
                if (isMultiSelectList && !dialogState.loading) {
                    SensorDetailSettingSheet(
                        title = viewModel.getSettingTranslatedTitle(dialogState.setting.name),
                        state = dialogState,
                        onDismiss = { viewModel.cancelSettingWithDialog() },
                        onSave = { updatedState -> onDialogSettingSubmitted(updatedState) },
                    )
                } else {
                    SensorDetailSettingDialog(
                        viewModel = viewModel,
                        state = dialogState,
                        onDismiss = { viewModel.cancelSettingWithDialog() },
                        onSubmit = { state -> onDialogSettingSubmitted(state) },
                    )
                }
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
                                    val noneSelected = stringResource(commonR.string.none_selected)
                                    val summary by produceState(
                                        initialValue = "",
                                        key1 = setting,
                                        key2 = summaryValues,
                                    ) {
                                        value = if (summaryValues.any()) {
                                            viewModel.getSettingEntries(setting, summaryValues)
                                                .joinToString(", ")
                                        } else {
                                            noneSelected
                                        }
                                    }
                                    SensorDetailRow(
                                        title = viewModel.getSettingTranslatedTitle(setting.name),
                                        summary = summary,
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sensor = dbSensor.map { it.sensor }.maxByOrNull { it.enabled }

    Surface(
        modifier = modifier,
        color = colorResource(commonR.color.colorSensorTopBackground),
    ) {
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier
                        .background(colorResource(commonR.color.colorSensorTopEnabled))
                        .then(enableBarModifier)
                } else {
                    enableBarModifier
                },
            ),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.then(rowModifier),
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
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val listSettingDialog = state.setting.valueType.listType
    val inputValue = remember(state.loading) { mutableStateOf(state.setting.value) }
    val checkedValue =
        remember(state.loading) { mutableStateListOf<String>().also { it.addAll(state.entriesSelected) } }

    MdcAlertDialog(
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    MdcAlertDialog(
        modifier = modifier,
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

/**
 * Bottom sheet for multi-select allow list sensor settings (apps, bluetooth, zones, beacons).
 * Uses [HAModalBottomSheet] following the Entity picker pattern with search, checkboxes,
 * and cancel/save actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailSettingSheet(
    title: String,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSave: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkedValue = remember {
        mutableStateListOf<String>().also { it.addAll(state.entriesSelected) }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(state.entries, searchQuery) {
        filterSettingEntries(state.entries, searchQuery)
    }
    val showSearch = state.entries.size > 10

    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val screenHeight = safeScreenHeight() - HADimens.SPACE16

    val consumeFlingNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .height(screenHeight)
                .nestedScroll(consumeFlingNestedScrollConnection)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, _ -> }
                },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(
                    horizontal = HADimens.SPACE4,
                    vertical = HADimens.SPACE3,
                ),
            )
            if (showSearch) {
                SettingSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredEntries, key = { (id) -> id }) { (id, entry) ->
                    SensorDetailSettingRow(
                        label = entry,
                        checked = checkedValue.contains(id),
                        multiple = true,
                        onClick = { isChecked ->
                            if (checkedValue.contains(id) && !isChecked) {
                                checkedValue.remove(id)
                            } else if (!checkedValue.contains(id) && isChecked) {
                                checkedValue.add(id)
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE3),
                horizontalArrangement = Arrangement.End,
            ) {
                HAPlainButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                )
                Spacer(modifier = Modifier.width(HADimens.SPACE2))
                HAFilledButton(
                    text = stringResource(commonR.string.save),
                    onClick = {
                        val joinedValue = checkedValue.joinToString().replace("[", "").replace("]", "")
                        onSave(state.copy().apply { setting.value = joinedValue })
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        label = { Text(stringResource(commonR.string.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(commonR.string.clear_search),
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * Filters setting entries by matching the query against entry labels (case-insensitive).
 * Returns all entries when the query is blank.
 */
internal fun filterSettingEntries(
    entries: List<Pair<String, String>>,
    query: String,
): List<Pair<String, String>> {
    val trimmed = query.trim()
    return if (trimmed.isBlank()) {
        entries
    } else {
        entries.filter { (_, label) -> label.contains(trimmed, ignoreCase = true) }
    }
}

@Composable
fun SensorDetailSettingRow(
    label: String,
    checked: Boolean,
    multiple: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = label.split("\n", limit = 2)
    val primaryText = parts[0]
    val secondaryText = parts.getOrNull(1)?.removeSurrounding("(", ")")

    Row(
        modifier = modifier
            .clickable { onClick(!checked) }
            .padding(horizontal = 12.dp)
            .heightIn(min = 64.dp)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    color = LocalHAColorScheme.current.colorTextPrimary,
                ),
            )
            if (secondaryText != null) {
                Spacer(Modifier.height(HADimens.SPACE1))
                Text(
                    text = secondaryText,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
            }
        }
    }
}
