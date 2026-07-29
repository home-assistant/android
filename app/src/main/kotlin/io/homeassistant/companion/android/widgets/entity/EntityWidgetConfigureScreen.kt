package io.homeassistant.companion.android.widgets.entity

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAInputChip
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState.Loaded
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.WidgetBackgroundTypeDropdown
import io.homeassistant.companion.android.widgets.WidgetTextColor
import io.homeassistant.companion.android.widgets.WidgetTextColorDropdown

/**
 * Configuration screen of the entity widget, bound to its [EntityWidgetConfigureViewModel].
 *
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 */
@Composable
internal fun EntityWidgetConfigureScreen(
    viewModel: EntityWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

    EntityWidgetConfigureContent(
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = viewModel::onServerSelected,
        onEntitySelected = viewModel::onEntitySelected,
        onAttributeAdded = viewModel::onAttributeAdded,
        onAttributeRemoved = viewModel::onAttributeRemoved,
        onCustomAttributeChanged = viewModel::onCustomAttributeChanged,
        onCustomAttributesAdded = viewModel::onCustomAttributesAdded,
        onLabelChanged = viewModel::onLabelChanged,
        onTextSizeChanged = viewModel::onTextSizeChanged,
        onStateSeparatorChanged = viewModel::onStateSeparatorChanged,
        onAttributeSeparatorChanged = viewModel::onAttributeSeparatorChanged,
        onTapActionSelected = viewModel::onTapActionSelected,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        onTextColorSelected = viewModel::onTextColorSelected,
        onActionClick = onActionClick,
    )
}

/** Stateless configuration screen for the entity widget. */
@Composable
internal fun EntityWidgetConfigureContent(
    state: EntityWidgetConfigureState,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String?) -> Unit,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onTextSizeChanged: (String) -> Unit,
    onStateSeparatorChanged: (String) -> Unit,
    onAttributeSeparatorChanged: (String) -> Unit,
    onTapActionSelected: (WidgetTapAction) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
    onActionClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.select_entity_to_display)) },
                onBackClick = onNavigate.takeIf { canNavigateBack },
                onCloseClick = onNavigate.takeIf { !canNavigateBack },
            )
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
                items = state.serversDropdownItems,
                selectedServerId = state.selectedServerId,
                showServerSelector = state.showServerSelector,
                onServerSelected = onServerSelected,
            )
            EntityPickerSection(
                displayEntities = state.entityDisplayState,
                selectedEntityId = state.selectedEntityId,
                onEntitySelected = onEntitySelected,
            )
            ConfigurationSections(
                state = state,
                onAttributeAdded = onAttributeAdded,
                onAttributeRemoved = onAttributeRemoved,
                onCustomAttributeChanged = onCustomAttributeChanged,
                onCustomAttributesAdded = onCustomAttributesAdded,
                onAttributeSeparatorChanged = onAttributeSeparatorChanged,
                onLabelChanged = onLabelChanged,
                onTextSizeChanged = onTextSizeChanged,
                onStateSeparatorChanged = onStateSeparatorChanged,
                onTapActionSelected = onTapActionSelected,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
                onTextColorSelected = onTextColorSelected,
            )

            ActionButton(
                labelRes = state.actionButtonLabel,
                enabled = state.isActionEnabled,
                onActionClick = onActionClick,
            )
        }
    }
}

/**
 * Everything describing how to display the selected entity, revealed once there is one.
 */
@Composable
private fun ColumnScope.ConfigurationSections(
    state: EntityWidgetConfigureState,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
    onAttributeSeparatorChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onTextSizeChanged: (String) -> Unit,
    onStateSeparatorChanged: (String) -> Unit,
    onTapActionSelected: (WidgetTapAction) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    AnimatedVisibility(visible = state.showConfiguration) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            AttributeSection(
                state = state,
                onAttributeAdded = onAttributeAdded,
                onAttributeRemoved = onAttributeRemoved,
                onCustomAttributeChanged = onCustomAttributeChanged,
                onCustomAttributesAdded = onCustomAttributesAdded,
                onAttributeSeparatorChanged = onAttributeSeparatorChanged,
            )
            TextOptionsSection(
                state = state,
                onTextSizeChanged = onTextSizeChanged,
                onStateSeparatorChanged = onStateSeparatorChanged,
                onLabelChanged = onLabelChanged,
            )
            TapActionSection(
                selectedTapAction = state.selectedTapAction,
                isToggleable = state.isToggleable,
                onTapActionSelected = onTapActionSelected,
            )
            AppearanceSection(
                selectedBackgroundType = state.selectedBackgroundType,
                dynamicColorAvailable = state.dynamicColorAvailable,
                textColorHex = state.textColorHex,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
                onTextColorSelected = onTextColorSelected,
            )
        }
    }
}

@Composable
private fun ServerSelector(
    items: List<HADropdownItem<Int>>,
    selectedServerId: Int,
    showServerSelector: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (!showServerSelector) return

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.server_select),
        placeholder = stringResource(commonR.string.server_select),
        modifier = Modifier.formControlWidth(),
        enabled = items.isNotEmpty(),
    )
}

@Composable
private fun EntityPickerSection(
    displayEntities: EntityDisplayState<EntityDisplayWithContext>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
) {
    EntityPicker(
        displayState = displayEntities,
        selectedEntityId = selectedEntityId,
        onSelectionChanged = onEntitySelected,
        modifier = Modifier.formControlWidth(),
    )
}

@Composable
private fun AttributeSection(
    state: EntityWidgetConfigureState,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
    onAttributeSeparatorChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier.formControlWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        AttributeSelector(
            unselectedAttributes = state.unselectedAttributes,
            selectedAttributeIds = state.selectedAttributeIds,
            customAttribute = state.customAttribute,
            onAttributeAdded = onAttributeAdded,
            onAttributeRemoved = onAttributeRemoved,
            onCustomAttributeChanged = onCustomAttributeChanged,
            onCustomAttributesAdded = onCustomAttributesAdded,
        )
        HATextField(
            value = state.attributeSeparator,
            onValueChange = onAttributeSeparatorChanged,
            label = { Text(stringResource(commonR.string.widget_attribute_separator_label)) },
            placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
            maxLines = 1,
        )
    }
}

@Composable
private fun TextOptionsSection(
    state: EntityWidgetConfigureState,
    onTextSizeChanged: (String) -> Unit,
    onStateSeparatorChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
) {
    HATextField(
        value = state.textSize,
        onValueChange = onTextSizeChanged,
        isError = state.textSizeError != null,
        label = { Text(stringResource(commonR.string.widget_text_size_label)) },
        supportingText = state.textSizeError?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        maxLines = 1,
    )

    HATextField(
        value = state.stateSeparator,
        onValueChange = onStateSeparatorChanged,
        label = { Text(stringResource(commonR.string.widget_state_separator_label)) },
        placeholder = { Text(stringResource(commonR.string.widget_separator_input_hint)) },
        maxLines = 1,
    )

    HATextField(
        value = state.label,
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

    val toggleLabel = stringResource(commonR.string.widget_tap_action_toggle)
    val refreshLabel = stringResource(commonR.string.refresh)
    val items = remember(toggleLabel, refreshLabel) {
        listOf(
            HADropdownItem(key = WidgetTapAction.TOGGLE, label = toggleLabel),
            HADropdownItem(key = WidgetTapAction.REFRESH, label = refreshLabel),
        )
    }

    HADropdownMenu(
        items = items,
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
    textColorHex: String?,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    WidgetBackgroundTypeDropdown(
        selected = selectedBackgroundType,
        dynamicColorAvailable = dynamicColorAvailable,
        onSelected = onBackgroundTypeSelected,
        modifier = Modifier.formControlWidth(),
    )

    if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
        // Widgets persist the resolved hex, so the Context needed to convert stays in the UI layer.
        val context = LocalContext.current
        val selected = remember(context, textColorHex) { WidgetTextColor.fromHex(context, textColorHex) }

        WidgetTextColorDropdown(
            selected = selected,
            onSelected = { onTextColorSelected(it.resolve(context)) },
            modifier = Modifier.formControlWidth(),
        )
    }
}

@Composable
private fun ActionButton(@StringRes labelRes: Int, enabled: Boolean, onActionClick: () -> Unit) {
    HAAccentButton(
        text = stringResource(labelRes),
        onClick = onActionClick,
        modifier = Modifier.formControlWidth(),
        enabled = enabled,
    )
}

private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()

@Composable
private fun AttributeSelector(
    unselectedAttributes: List<String>,
    selectedAttributeIds: List<String>,
    customAttribute: String,
    onAttributeAdded: (String) -> Unit,
    onAttributeRemoved: (String) -> Unit,
    onCustomAttributeChanged: (String) -> Unit,
    onCustomAttributesAdded: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        HATextField(
            value = customAttribute,
            onValueChange = onCustomAttributeChanged,
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

        AttributeChips(attributeIds = unselectedAttributes, selected = false, onClick = onAttributeAdded)
        AttributeChips(attributeIds = selectedAttributeIds, selected = true, onClick = onAttributeRemoved)
    }
}

/**
 * Chips for one group of attributes: the ones still available to add, or the ones already selected.
 *
 * @param attributeIds Attributes to show, nothing is rendered when empty.
 * @param selected Whether these attributes are part of the selection, which decides whether a chip
 * offers to remove it or to add it.
 * @param onClick Invoked with the attribute of the clicked chip.
 */
@Composable
private fun AttributeChips(attributeIds: List<String>, selected: Boolean, onClick: (String) -> Unit) {
    if (attributeIds.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        attributeIds.forEach { attributeId ->
            HAInputChip(
                text = attributeId,
                onClick = { onClick(attributeId) },
                selected = selected,
                trailingIcon = if (selected) Icons.Default.Close else Icons.Default.Add,
                trailingIconContentDescription = stringResource(
                    if (selected) commonR.string.search_clear_selection else commonR.string.widget_attribute_add,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun EntityWidgetConfigureContentPreview() {
    HAThemeForPreview {
        EntityWidgetConfigureContent(
            state = previewEntityWidgetConfigureState,
            snackbarHostState = remember { SnackbarHostState() },
            canNavigateBack = false,
            onNavigate = {},
            onServerSelected = {},
            onEntitySelected = {},
            onAttributeAdded = {},
            onAttributeRemoved = {},
            onCustomAttributeChanged = {},
            onCustomAttributesAdded = {},
            onLabelChanged = {},
            onTextSizeChanged = {},
            onStateSeparatorChanged = {},
            onAttributeSeparatorChanged = {},
            onTapActionSelected = {},
            onBackgroundTypeSelected = {},
            onTextColorSelected = {},
            onActionClick = {},
        )
    }
}

private val previewEntityWidgetConfigureState = EntityWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    entityDisplayState = Loaded(listOf(EntityDisplayWithContext(EntityDisplayWithoutContext(previewEntity1)))),
    selectedEntityId = previewEntity1.entityId,
    availableAttributes = listOf("brightness", "friendly_name"),
    selectedAttributeIds = listOf("brightness"),
    label = "Office light",
    textSize = "30",
    stateSeparator = " - ",
    attributeSeparator = ", ",
    selectedTapAction = WidgetTapAction.TOGGLE,
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
