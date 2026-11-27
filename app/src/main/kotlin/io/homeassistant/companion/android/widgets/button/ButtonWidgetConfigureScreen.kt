package io.homeassistant.companion.android.widgets.button

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.PopupProperties
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.github.timoptr.mdiicons.generated.Eye
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.configure.WidgetAppearanceSection
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScaffold
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScreen
import io.homeassistant.companion.android.widgets.configure.formControlWidth

@SuppressLint("ComposeViewModelForwarding")
@Composable
internal fun ButtonWidgetConfigureScreen(
    viewModel: ButtonWidgetViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    val callbacks = ButtonWidgetConfigureCallbacks(
        onServerSelected = viewModel::onServerSelected,
        onActionTextUpdated = viewModel::updateActionText,
        onDynamicFieldUpdated = viewModel::updateDynamicField,
        onIconSelected = viewModel::selectIcon,
        onAddFieldDialogOkClicked = viewModel::addDynamicField,
        onLabelUpdated = { viewModel.updateOption(label = it) },
        onBackgroundTypeSelected = { viewModel.updateOption(selectedBackgroundType = it) },
        onTextColorSelected = { viewModel.updateOption(textColorHex = it) },
        onRequireAuthenticationChecked = { viewModel.updateOption(requiresAuthentication = it) },
    )
    WidgetConfigureScreen(viewModel) { state, snackbarHostState ->
        var showAddFieldDialog by remember { mutableStateOf(false) }
        var showIconDialog by remember { mutableStateOf(false) }

        if (showAddFieldDialog) {
            AddFieldDialog(
                action = state.actionText,
                onCancel = { showAddFieldDialog = false },
                onOk = { actionField ->
                    if (!state.dynamicFields.any { it.field == state.actionText }) {
                        callbacks.onAddFieldDialogOkClicked(state.dynamicFields.size, actionField)
                    }
                    showAddFieldDialog = false
                },
            )
        }

        if (showIconDialog) {
            IconDialog(
                onSelect = {
                    showIconDialog = false
                    callbacks.onIconSelected(it)
                },
                onDismissRequest = { showIconDialog = false },
            )
        }

        ButtonWidgetConfigureForm(
            state = state,
            snackbarHostState = snackbarHostState,
            canNavigateBack = canNavigateBack,
            onNavigate = onNavigate,
            onActionClick = onActionClick,
            callbacks = callbacks,
            shouldShowAddFieldDialog = { showAddFieldDialog = it },
            shouldShowIconDialog = { showIconDialog = it },
        )
    }
}

@Composable
internal fun ButtonWidgetConfigureForm(
    state: ButtonWidgetConfigureState,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
    callbacks: ButtonWidgetConfigureCallbacks,
    shouldShowAddFieldDialog: (Boolean) -> Unit,
    shouldShowIconDialog: (Boolean) -> Unit,
) {
    WidgetConfigureScaffold(
        title = commonR.string.configure_action,
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = callbacks.onServerSelected,
        onActionClick = onActionClick,
    ) {
        ActionFieldSection(
            actionText = state.actionText,
            onActionTextChanged = callbacks.onActionTextUpdated,
            serverActions = state.serverActions,
            dynamicFields = state.dynamicFields,
            domainEntities = state.domainEntities,
            onDynamicFieldUpdated = callbacks.onDynamicFieldUpdated,
        )

        HAAccentButton(
            text = stringResource(commonR.string.add_action_data_field),
            onClick = { shouldShowAddFieldDialog(true) },
            modifier = Modifier.align(Alignment.End),
        )

        IconSelector(
            icon = state.selectedIcon,
            onIconSelectorClicked = { shouldShowIconDialog(true) },
        )

        HATextField(
            label = { Text(text = stringResource(commonR.string.label)) },
            value = state.label,
            onValueChange = { callbacks.onLabelUpdated(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        WidgetAppearanceSection(
            selectedBackgroundType = state.selectedBackgroundType,
            dynamicColorAvailable = state.dynamicColorAvailable,
            textColorHex = state.textColorHex,
            onBackgroundTypeSelected = callbacks.onBackgroundTypeSelected,
            onTextColorSelected = callbacks.onTextColorSelected,
        )

        AuthenticationCheckbox(
            requiresAuthentication = state.requiresAuthentication,
            onCheckedChange = { callbacks.onRequireAuthenticationChecked(it) },
            modifier = Modifier.align(Alignment.Start),
        )
    }
}

@Composable
fun ActionFieldSection(
    actionText: String,
    onActionTextChanged: (String) -> Unit,
    serverActions: List<Action>,
    dynamicFields: List<ActionFieldBinder>,
    domainEntities: List<String>,
    onDynamicFieldUpdated: (Int, String) -> Unit,
) {
    ActionTextFieldInput(actionText, onActionTextChanged, serverActions)

    if (dynamicFields.isNotEmpty()) {
        dynamicFields.forEachIndexed { index, fieldBinder ->
            DynamicFieldInput(index, fieldBinder, domainEntities, onDynamicFieldUpdated)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTextFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    serverActions: List<Action>,
    modifier: Modifier = Modifier,
) {
    var isActionDropdownExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isActionDropdownExpanded && serverActions.isNotEmpty(),
        onExpandedChange = { isActionDropdownExpanded = it },
        modifier = modifier,
    ) {
        HATextField(
            label = { Text(text = stringResource(commonR.string.label_action)) },
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                isActionDropdownExpanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    isActionDropdownExpanded = state.hasFocus
                },
        )
        ExposedDropdownMenu(
            expanded = isActionDropdownExpanded && serverActions.isNotEmpty(),
            onDismissRequest = {
                isActionDropdownExpanded = false
            },
        ) {
            serverActions.forEach { action ->
                val text = "${action.domain}.${action.action}"
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = {
                        onValueChange(text)
                        isActionDropdownExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFieldInput(
    index: Int,
    field: ActionFieldBinder,
    domainEntities: List<String>,
    onDynamicFieldUpdated: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEntityDropdownExpanded by remember { mutableStateOf(false) }
    val initialValue = when (val value = field.value) {
        is List<*> -> value.joinToString(", ")
        is String -> value
        else -> ""
    }
    Box {
        HATextField(
            label = { Text(text = field.field) },
            value = initialValue,
            onValueChange = { onDynamicFieldUpdated(index, it) },
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (field.field == "entity_id") {
                        isEntityDropdownExpanded = state.hasFocus
                    }
                },
        )
        if (field.field == "entity_id" && domainEntities.isNotEmpty()) {
            DropdownMenu(
                expanded = isEntityDropdownExpanded,
                onDismissRequest = {
                    isEntityDropdownExpanded = false
                },
                properties = PopupProperties(focusable = false),
            ) {
                domainEntities.forEach { entity ->
                    DropdownMenuItem(
                        text = { Text(text = entity) },
                        onClick = {
                            val current = initialValue.trim().removeSuffix(",")
                            val newText = if (current.isEmpty()) "$entity, " else "$current, $entity, "
                            onDynamicFieldUpdated(index, newText)
                            isEntityDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun IconSelector(icon: MdiIcon, onIconSelectorClicked: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        val colorScheme = LocalHAColorScheme.current
        HASettingsCard(
            modifier = Modifier
                .formControlWidth()
                .clickable(enabled = true, onClick = onIconSelectorClicked),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                ) {
                    Text(
                        text = stringResource(commonR.string.icon),
                        style = HATextStyle.BodyMedium,
                        color = colorScheme.colorTextSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        imageVector = icon.rememberImageVector(),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorResource(commonR.color.colorIcon)),
                        modifier = Modifier.size(HADimens.SPACE6),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = HADimens.SPACE2),
                    ) {
                        Text(
                            text = icon.name,
                            style = HATextStyle.Body,
                            color = colorScheme.colorTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Mdi.ChevronDown.rememberImageVector(),
                        contentDescription = null,
                        tint = colorScheme.colorTextSecondary,
                        modifier = Modifier
                            .size(HADimens.SPACE5)
                            .align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
fun AddFieldDialog(
    action: String,
    onCancel: (() -> Unit)?,
    onOk: ((ActionFieldBinder) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var inputValue by remember { mutableStateOf("") }

    MdcAlertDialog(
        modifier = modifier,
        onDismissRequest = { },
        title = { Text(text = "Field") },
        content = {
            HATextField(
                value = inputValue,
                onValueChange = { input: String ->
                    inputValue = input
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        },
        onCancel = onCancel,
        onSave = null,
        onOK = {
            val field = ActionFieldBinder(action, inputValue)
            onOk?.invoke(field)
        },
    )
}

@Composable
fun AuthenticationCheckbox(
    requiresAuthentication: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        HACheckbox(
            checked = requiresAuthentication,
            onCheckedChange = { onCheckedChange(it) },
        )
        Text(
            text = stringResource(commonR.string.widget_checkbox_require_authentication),
            color = colorScheme.colorTextPrimary,
        )
    }
}

@Composable
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun ButtonWidgetConfigureScreenPreview() {
    HAThemeForPreview {
        ButtonWidgetConfigureForm(
            state = previewEntityWidgetConfigureState,
            snackbarHostState = remember { SnackbarHostState() },
            canNavigateBack = false,
            onNavigate = { },
            onActionClick = { },
            callbacks = ButtonWidgetConfigureCallbacks(
                onActionTextUpdated = { },
                onServerSelected = { },
                onDynamicFieldUpdated = { i, string -> },
                onIconSelected = { },
                onAddFieldDialogOkClicked = { i, binder -> },
                onLabelUpdated = { },
                onBackgroundTypeSelected = { },
                onTextColorSelected = { },
                onRequireAuthenticationChecked = { },
            ),
            shouldShowAddFieldDialog = { },
            shouldShowIconDialog = { },
        )
    }
}

private val previewEntityWidgetConfigureState = ButtonWidgetConfigureState(
    serversDropdownItems = listOf(
        HADropdownItem(previewServer1.id, previewServer1.friendlyName),
        HADropdownItem(previewServer2.id, previewServer2.friendlyName),
    ),
    selectedServerId = 0,
    dynamicFields = listOf(ActionFieldBinder("Test Action", "Test", 1)),
    selectedIcon = Mdi.Eye,
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
)
