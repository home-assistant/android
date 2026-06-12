package io.homeassistant.companion.android.widgets.entity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

@Composable
internal fun EntityWidgetConfigureScreen(
    viewModel: EntityWidgetConfigureViewModel,
    dynamicColorAvailable: Boolean,
    onActionClick: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle(emptyList())
    val entities by viewModel.entities.collectAsStateWithLifecycle()
    val selectedEntity = entities.firstOrNull { it.entityId == viewModel.selectedEntityId }

    EntityWidgetConfigureView(
        servers = servers,
        selectedServerId = viewModel.selectedServerId,
        onServerSelected = viewModel::onServerSelected,
        entities = entities,
        selectedEntityId = viewModel.selectedEntityId,
        onEntitySelected = viewModel::onEntitySelected,
        availableAttributes = selectedEntity?.attributes?.keys.orEmpty().sorted(),
        appendAttributes = viewModel.appendAttributes,
        onAppendAttributesChanged = viewModel::onAppendAttributesChanged,
        selectedAttributeIds = viewModel.selectedAttributeIds,
        onAttributeAdded = viewModel::onAttributeAdded,
        onAttributeRemoved = viewModel::onAttributeRemoved,
        label = viewModel.label,
        onLabelChanged = viewModel::onLabelChanged,
        textSize = viewModel.textSize,
        onTextSizeChanged = viewModel::onTextSizeChanged,
        stateSeparator = viewModel.stateSeparator,
        onStateSeparatorChanged = viewModel::onStateSeparatorChanged,
        attributeSeparator = viewModel.attributeSeparator,
        onAttributeSeparatorChanged = viewModel::onAttributeSeparatorChanged,
        isToggleable = selectedEntity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS,
        selectedTapAction = viewModel.selectedTapAction,
        onTapActionSelected = viewModel::onTapActionSelected,
        selectedBackgroundType = viewModel.selectedBackgroundType,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        dynamicColorAvailable = dynamicColorAvailable,
        selectedTextColor = viewModel.selectedTextColor,
        onTextColorSelected = viewModel::onTextColorSelected,
        isUpdateWidget = viewModel.isUpdateWidget,
        onActionClick = onActionClick,
    )
}

@Composable
private fun EntityWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
    availableAttributes: List<String>,
    appendAttributes: Boolean,
    onAppendAttributesChanged: (Boolean) -> Unit,
    selectedAttributeIds: List<String>,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    label: String,
    onLabelChanged: (String) -> Unit,
    textSize: String,
    onTextSizeChanged: (String) -> Unit,
    stateSeparator: String,
    onStateSeparatorChanged: (String) -> Unit,
    attributeSeparator: String,
    onAttributeSeparatorChanged: (String) -> Unit,
    isToggleable: Boolean,
    selectedTapAction: WidgetTapAction,
    onTapActionSelected: (WidgetTapAction) -> Unit,
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    dynamicColorAvailable: Boolean,
    selectedTextColor: EntityWidgetTextColor,
    onTextColorSelected: (EntityWidgetTextColor) -> Unit,
    isUpdateWidget: Boolean,
    onActionClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HATopBar(title = { Text(stringResource(commonR.string.select_entity_to_display)) })
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            if (servers.size > 1 ||
                (isUpdateWidget && servers.none { it.id == selectedServerId })
            ) {
                ServerExposedDropdownMenu(
                    servers = servers,
                    current = selectedServerId,
                    onSelected = onServerSelected,
                )
            }

            EntityPicker(
                entities = entities,
                selectedEntityId = selectedEntityId,
                onEntitySelectedId = onEntitySelected,
                onEntityCleared = { onEntitySelected(null) },
            )

            CheckboxRow(
                text = stringResource(commonR.string.entity_attribute_checkbox),
                checked = appendAttributes,
                onCheckedChange = onAppendAttributesChanged,
            )

            if (appendAttributes) {
                AttributeSelector(
                    availableAttributes = availableAttributes,
                    selectedAttributeIds = selectedAttributeIds,
                    onAttributeAdded = onAttributeAdded,
                    onAttributeRemoved = onAttributeRemoved,
                )
                HATextField(
                    value = attributeSeparator,
                    onValueChange = onAttributeSeparatorChanged,
                    label = { Text(stringResource(commonR.string.widget_attribute_separator_label)) },
                    placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
                    maxLines = 1,
                )
            }

            HATextField(
                value = textSize,
                onValueChange = onTextSizeChanged,
                label = { Text(stringResource(commonR.string.widget_text_size_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                maxLines = 1,
            )

            HATextField(
                value = stateSeparator,
                onValueChange = onStateSeparatorChanged,
                label = { Text(stringResource(commonR.string.widget_state_separator_label)) },
                placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
                maxLines = 1,
            )

            HATextField(
                value = label,
                onValueChange = onLabelChanged,
                label = { Text(stringResource(commonR.string.label_label)) },
                placeholder = { Text(stringResource(commonR.string.widget_text_hint_label)) },
                maxLines = 1,
            )

            if (isToggleable) {
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
                )
            }

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
                )
            }

            HAAccentButton(
                text = stringResource(if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget),
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
) {
    val unselectedAttributes = availableAttributes.filterNot(selectedAttributeIds::contains)
    var customAttribute by rememberSaveable { mutableStateOf("") }
    val addCustomAttributes = {
        customAttribute
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach(onAttributeAdded)
        customAttribute = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HADropdownMenu(
            items = unselectedAttributes.map { HADropdownItem(key = it, label = it) },
            selectedKey = null,
            onItemSelected = onAttributeAdded,
            label = stringResource(commonR.string.label_attribute),
            placeholder = stringResource(commonR.string.widget_attribute_add),
            enabled = unselectedAttributes.isNotEmpty(),
        )

        HATextField(
            value = customAttribute,
            onValueChange = { customAttribute = it },
            label = { Text(stringResource(commonR.string.widget_attribute_add)) },
            trailingIcon = {
                IconButton(
                    onClick = addCustomAttributes,
                    enabled = customAttribute.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(commonR.string.widget_attribute_add),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addCustomAttributes() }),
            maxLines = 1,
        )

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
            selectedServerId = previewServer1.id,
            onServerSelected = {},
            entities = listOf(previewEntity1),
            selectedEntityId = previewEntity1.entityId,
            onEntitySelected = {},
            availableAttributes = listOf("brightness", "friendly_name"),
            appendAttributes = true,
            onAppendAttributesChanged = {},
            selectedAttributeIds = listOf("brightness"),
            onAttributeAdded = {},
            onAttributeRemoved = {},
            label = "Office light",
            onLabelChanged = {},
            textSize = "30",
            onTextSizeChanged = {},
            stateSeparator = " - ",
            onStateSeparatorChanged = {},
            attributeSeparator = ", ",
            onAttributeSeparatorChanged = {},
            isToggleable = true,
            selectedTapAction = WidgetTapAction.TOGGLE,
            onTapActionSelected = {},
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            onBackgroundTypeSelected = {},
            dynamicColorAvailable = true,
            selectedTextColor = EntityWidgetTextColor.WHITE,
            onTextColorSelected = {},
            isUpdateWidget = false,
            onActionClick = {},
        )
    }
}
