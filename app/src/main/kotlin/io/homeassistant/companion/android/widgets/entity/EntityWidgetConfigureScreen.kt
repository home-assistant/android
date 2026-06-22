package io.homeassistant.companion.android.widgets.entity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ENTITY_WIDGET_CUSTOM_ATTRIBUTE_TAG = "entity_widget_custom_attribute"
internal const val ENTITY_WIDGET_ACTION_BUTTON_TAG = "entity_widget_action_button"

private data class SelectedEntityData(val entity: Entity? = null, val availableAttributes: List<String> = emptyList())

@Composable
internal fun EntityWidgetConfigureScreen(
    viewModel: EntityWidgetConfigureViewModel,
    dynamicColorAvailable: Boolean,
    onActionClick: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle(emptyList())
    val entities by viewModel.entities.collectAsStateWithLifecycle()
    val entityRegistry by viewModel.entityRegistry.collectAsStateWithLifecycle()
    val deviceRegistry by viewModel.deviceRegistry.collectAsStateWithLifecycle()
    val areaRegistry by viewModel.areaRegistry.collectAsStateWithLifecycle()
    val viewState = viewModel.viewState
    val selectedEntityData by produceState(
        initialValue = SelectedEntityData(),
        entities,
        viewState.selectedEntityId,
    ) {
        value = withContext(Dispatchers.Default) {
            val entity = entities.firstOrNull { it.entityId == viewState.selectedEntityId }
            SelectedEntityData(
                entity = entity,
                availableAttributes = entity?.attributes?.keys.orEmpty().sorted(),
            )
        }
    }
    val selectedEntity = selectedEntityData.entity

    LaunchedEffect(selectedEntity?.entityId, selectedEntity?.friendlyName) {
        viewModel.onSelectedEntityLoaded(selectedEntity)
    }

    EntityWidgetConfigureView(
        servers = servers,
        viewState = viewState,
        onServerSelected = viewModel::onServerSelected,
        entities = entities,
        onEntitySelected = viewModel::onEntitySelected,
        entityRegistry = entityRegistry,
        deviceRegistry = deviceRegistry,
        areaRegistry = areaRegistry,
        availableAttributes = selectedEntityData.availableAttributes,
        onAppendAttributesChanged = viewModel::onAppendAttributesChanged,
        onAttributeAdded = viewModel::onAttributeAdded,
        onAttributeRemoved = viewModel::onAttributeRemoved,
        onCustomAttributeChanged = viewModel::onCustomAttributeChanged,
        onCustomAttributesAdded = viewModel::onCustomAttributesAdded,
        onLabelChanged = viewModel::onLabelChanged,
        onTextSizeChanged = viewModel::onTextSizeChanged,
        onStateSeparatorChanged = viewModel::onStateSeparatorChanged,
        onAttributeSeparatorChanged = viewModel::onAttributeSeparatorChanged,
        isToggleable = selectedEntity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS,
        onTapActionSelected = viewModel::onTapActionSelected,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        dynamicColorAvailable = dynamicColorAvailable,
        onTextColorSelected = viewModel::onTextColorSelected,
        onErrorShown = viewModel::onErrorShown,
        onActionClick = onActionClick,
    )
}

@Composable
internal fun EntityWidgetConfigureView(
    servers: List<Server>,
    viewState: EntityWidgetConfigureViewState,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    onEntitySelected: (String?) -> Unit,
    availableAttributes: List<String>,
    onAppendAttributesChanged: (Boolean) -> Unit,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onTextSizeChanged: (String) -> Unit,
    onStateSeparatorChanged: (String) -> Unit,
    onAttributeSeparatorChanged: (String) -> Unit,
    isToggleable: Boolean,
    onTapActionSelected: (WidgetTapAction) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    dynamicColorAvailable: Boolean,
    onTextColorSelected: (EntityWidgetTextColor) -> Unit,
    onErrorShown: () -> Unit,
    entityRegistry: List<EntityRegistryResponse>? = null,
    deviceRegistry: List<DeviceRegistryResponse>? = null,
    areaRegistry: List<AreaRegistryResponse>? = null,
    onActionClick: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val creationError = stringResource(commonR.string.widget_creation_error)
    val updateError = stringResource(commonR.string.widget_update_error)

    LaunchedEffect(viewState.error) {
        val error = viewState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (error) {
                EntityWidgetConfigureError.CREATE -> creationError
                EntityWidgetConfigureError.UPDATE -> updateError
            },
        )
        onErrorShown()
    }

    Scaffold(
        topBar = {
            HATopBar(title = { Text(stringResource(commonR.string.select_entity_to_display)) })
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(HADimens.SPACE4)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            ServerSelector(
                servers = servers,
                selectedServerId = viewState.selectedServerId,
                isUpdateWidget = viewState.isUpdateWidget,
                onServerSelected = onServerSelected,
            )
            EntityPickerSection(
                entities = entities,
                selectedEntityId = viewState.selectedEntityId,
                entityRegistry = entityRegistry,
                deviceRegistry = deviceRegistry,
                areaRegistry = areaRegistry,
                onEntitySelected = onEntitySelected,
            )
            AttributeSection(
                viewState = viewState,
                availableAttributes = availableAttributes,
                onAppendAttributesChanged = onAppendAttributesChanged,
                onAttributeAdded = onAttributeAdded,
                onAttributeRemoved = onAttributeRemoved,
                onCustomAttributeChanged = onCustomAttributeChanged,
                onCustomAttributesAdded = onCustomAttributesAdded,
                onAttributeSeparatorChanged = onAttributeSeparatorChanged,
            )
            TextOptionsSection(
                viewState = viewState,
                onTextSizeChanged = onTextSizeChanged,
                onStateSeparatorChanged = onStateSeparatorChanged,
                onLabelChanged = onLabelChanged,
            )
            TapActionSection(
                selectedTapAction = viewState.selectedTapAction,
                isToggleable = isToggleable,
                onTapActionSelected = onTapActionSelected,
            )
            AppearanceSection(
                selectedBackgroundType = viewState.selectedBackgroundType,
                dynamicColorAvailable = dynamicColorAvailable,
                selectedTextColor = viewState.selectedTextColor,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
                onTextColorSelected = onTextColorSelected,
            )
            ActionButton(
                isUpdateWidget = viewState.isUpdateWidget,
                enabled = viewState.isActionEnabled,
                onActionClick = onActionClick,
            )
        }
    }
}

@Composable
private fun ServerSelector(
    servers: List<Server>,
    selectedServerId: Int,
    isUpdateWidget: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (servers.size <= 1 && !(isUpdateWidget && servers.none { it.id == selectedServerId })) return

    HADropdownMenu(
        items = servers.map {
            HADropdownItem(key = it.id, label = it.friendlyName)
        },
        selectedKey = selectedServerId.takeIf { serverId ->
            servers.any { it.id == serverId }
        },
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.server_select),
        placeholder = stringResource(commonR.string.server_select),
        modifier = Modifier.formControlWidth(),
        enabled = servers.isNotEmpty(),
    )
}

@Composable
private fun EntityPickerSection(
    entities: List<Entity>,
    selectedEntityId: String?,
    entityRegistry: List<EntityRegistryResponse>? = null,
    deviceRegistry: List<DeviceRegistryResponse>? = null,
    areaRegistry: List<AreaRegistryResponse>? = null,
    onEntitySelected: (String?) -> Unit,
) {
    EntityPicker(
        entities = entities,
        selectedEntityId = selectedEntityId,
        onEntitySelectedId = onEntitySelected,
        onEntityCleared = { onEntitySelected(null) },
        modifier = Modifier.formControlWidth(),
        entityRegistry = entityRegistry,
        deviceRegistry = deviceRegistry,
        areaRegistry = areaRegistry,
    )
}

@Composable
private fun AttributeSection(
    viewState: EntityWidgetConfigureViewState,
    availableAttributes: List<String>,
    onAppendAttributesChanged: (Boolean) -> Unit,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
    onAttributeSeparatorChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier.formControlWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        CheckboxRow(
            text = stringResource(commonR.string.entity_attribute_checkbox),
            checked = viewState.appendAttributes,
            onCheckedChange = onAppendAttributesChanged,
        )

        AnimatedVisibility(visible = viewState.appendAttributes) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                AttributeSelector(
                    availableAttributes = availableAttributes,
                    selectedAttributeIds = viewState.selectedAttributeIds,
                    customAttribute = viewState.customAttribute,
                    onAttributeAdded = onAttributeAdded,
                    onAttributeRemoved = onAttributeRemoved,
                    onCustomAttributeChanged = onCustomAttributeChanged,
                    onCustomAttributesAdded = onCustomAttributesAdded,
                )
                HATextField(
                    value = viewState.attributeSeparator,
                    onValueChange = onAttributeSeparatorChanged,
                    label = { Text(stringResource(commonR.string.widget_attribute_separator_label)) },
                    placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TextOptionsSection(
    viewState: EntityWidgetConfigureViewState,
    onTextSizeChanged: (String) -> Unit,
    onStateSeparatorChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
) {
    HATextField(
        value = viewState.textSize,
        onValueChange = onTextSizeChanged,
        label = { Text(stringResource(commonR.string.widget_text_size_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        maxLines = 1,
    )

    HATextField(
        value = viewState.stateSeparator,
        onValueChange = onStateSeparatorChanged,
        label = { Text(stringResource(commonR.string.widget_state_separator_label)) },
        placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
        maxLines = 1,
    )

    HATextField(
        value = viewState.label,
        onValueChange = onLabelChanged,
        label = { Text(stringResource(commonR.string.label_label)) },
        placeholder = { Text(stringResource(commonR.string.widget_text_hint_label)) },
        maxLines = 1,
    )
}

@Composable
private fun TapActionSection(
    selectedTapAction: WidgetTapAction,
    isToggleable: Boolean,
    onTapActionSelected: (WidgetTapAction) -> Unit,
) {
    if (!isToggleable) return

    HADropdownMenu(
        items = listOf(
            HADropdownItem(
                key = WidgetTapAction.TOGGLE,
                label = stringResource(commonR.string.widget_tap_action_toggle),
            ),
            HADropdownItem(
                key = WidgetTapAction.REFRESH,
                label = stringResource(commonR.string.refresh),
            ),
        ),
        selectedKey = selectedTapAction,
        onItemSelected = onTapActionSelected,
        label = stringResource(commonR.string.widget_tap_action_label),
        modifier = Modifier.formControlWidth(),
    )
}

@Composable
private fun AppearanceSection(
    selectedBackgroundType: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    selectedTextColor: EntityWidgetTextColor,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (EntityWidgetTextColor) -> Unit,
) {
    HADropdownMenu(
        items = buildList {
            if (dynamicColorAvailable) {
                add(
                    HADropdownItem(
                        key = WidgetBackgroundType.DYNAMICCOLOR,
                        label = stringResource(commonR.string.widget_background_type_dynamiccolor),
                    ),
                )
            }
            add(
                HADropdownItem(
                    key = WidgetBackgroundType.DAYNIGHT,
                    label = stringResource(commonR.string.widget_background_type_daynight),
                ),
            )
            add(
                HADropdownItem(
                    key = WidgetBackgroundType.TRANSPARENT,
                    label = stringResource(commonR.string.widget_background_type_transparent),
                ),
            )
        },
        selectedKey = selectedBackgroundType,
        onItemSelected = onBackgroundTypeSelected,
        label = stringResource(commonR.string.widget_background_type_label),
        modifier = Modifier.formControlWidth(),
    )

    if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
        HADropdownMenu(
            items = listOf(
                HADropdownItem(
                    key = EntityWidgetTextColor.WHITE,
                    label = stringResource(commonR.string.widget_text_color_white),
                ),
                HADropdownItem(
                    key = EntityWidgetTextColor.BLACK,
                    label = stringResource(commonR.string.widget_text_color_black),
                ),
            ),
            selectedKey = selectedTextColor,
            onItemSelected = onTextColorSelected,
            label = stringResource(commonR.string.widget_text_color_label),
            modifier = Modifier.formControlWidth(),
        )
    }
}

@Composable
private fun ActionButton(isUpdateWidget: Boolean, enabled: Boolean, onActionClick: () -> Unit) {
    HAAccentButton(
        text = stringResource(
            if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget,
        ),
        onClick = onActionClick,
        modifier = Modifier
            .formControlWidth()
            .testTag(ENTITY_WIDGET_ACTION_BUTTON_TAG),
        enabled = enabled,
    )
}

private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()

@Composable
private fun CheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        HACheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = text,
            style = HATextStyle.Body,
            color = LocalHAColorScheme.current.colorTextPrimary,
        )
    }
}

@Composable
private fun AttributeSelector(
    availableAttributes: List<String>,
    selectedAttributeIds: List<String>,
    customAttribute: String,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
) {
    val unselectedAttributes = availableAttributes.filterNot(selectedAttributeIds::contains)

    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HATextField(
            value = customAttribute,
            onValueChange = onCustomAttributeChanged,
            modifier = Modifier.testTag(ENTITY_WIDGET_CUSTOM_ATTRIBUTE_TAG),
            label = { Text(stringResource(commonR.string.widget_attribute_add)) },
            placeholder = { Text(stringResource(commonR.string.label_attribute)) },
            trailingIcon = {
                IconButton(
                    onClick = onCustomAttributesAdded,
                    enabled = customAttribute.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(commonR.string.widget_attribute_add),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCustomAttributesAdded() }),
            maxLines = 1,
        )

        if (unselectedAttributes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                unselectedAttributes.forEach { attributeId ->
                    InputChip(
                        selected = false,
                        onClick = { onAttributeAdded(attributeId) },
                        label = { Text(attributeId) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(commonR.string.widget_attribute_add),
                            )
                        },
                    )
                }
            }
        }

        if (selectedAttributeIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                selectedAttributeIds.forEach { attributeId ->
                    InputChip(
                        selected = true,
                        onClick = { onAttributeRemoved(attributeId) },
                        label = { Text(attributeId) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(commonR.string.search_clear_selection),
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = LocalHAColorScheme.current.colorFillPrimaryNormalActive,
                            selectedLabelColor = LocalHAColorScheme.current.colorTextPrimary,
                            selectedTrailingIconColor = LocalHAColorScheme.current.colorTextSecondary,
                        ),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun EntityWidgetConfigureViewPreview() {
    HAThemeForPreview {
        EntityWidgetConfigureView(
            servers = listOf(previewServer1, previewServer2),
            viewState = EntityWidgetConfigureViewState(
                selectedServerId = previewServer1.id,
                selectedEntityId = previewEntity1.entityId,
                appendAttributes = true,
                selectedAttributeIds = listOf("brightness"),
                label = "Office light",
                textSize = "30",
                stateSeparator = " - ",
                attributeSeparator = ", ",
                selectedTapAction = WidgetTapAction.TOGGLE,
                selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
                selectedTextColor = EntityWidgetTextColor.WHITE,
            ),
            onServerSelected = {},
            entities = listOf(previewEntity1),
            onEntitySelected = {},
            entityRegistry = null,
            deviceRegistry = null,
            areaRegistry = null,
            availableAttributes = listOf("brightness", "friendly_name"),
            onAppendAttributesChanged = {},
            onAttributeAdded = {},
            onAttributeRemoved = {},
            onCustomAttributeChanged = {},
            onCustomAttributesAdded = {},
            onLabelChanged = {},
            onTextSizeChanged = {},
            onStateSeparatorChanged = {},
            onAttributeSeparatorChanged = {},
            isToggleable = true,
            onTapActionSelected = {},
            onBackgroundTypeSelected = {},
            dynamicColorAvailable = true,
            onTextColorSelected = {},
            onErrorShown = {},
            onActionClick = {},
        )
    }
}
