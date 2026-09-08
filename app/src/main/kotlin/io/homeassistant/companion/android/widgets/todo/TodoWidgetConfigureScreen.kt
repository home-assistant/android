package io.homeassistant.companion.android.widgets.todo

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.WidgetBackgroundTypeDropdown
import io.homeassistant.companion.android.widgets.WidgetTextColor
import io.homeassistant.companion.android.widgets.WidgetTextColorDropdown

/**
 * Configuration screen of the to-do widget, bound to its [TodoWidgetConfigureViewModel].
 *
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 */
@Composable
internal fun TodoWidgetConfigureScreen(
    viewModel: TodoWidgetConfigureViewModel,
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

    TodoWidgetConfigureContent(
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = viewModel::onServerSelected,
        onEntitySelected = viewModel::onEntitySelected,
        onShowCompletedChanged = viewModel::onShowCompletedChanged,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        onTextColorSelected = viewModel::onTextColorSelected,
        onActionClick = onActionClick,
    )
}

/** Stateless configuration screen for the to-do widget. */
@Composable
internal fun TodoWidgetConfigureContent(
    state: TodoWidgetConfigureState,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String?) -> Unit,
    onShowCompletedChanged: (Boolean) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
    onActionClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.widget_todo_label)) },
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
            EntityPicker(
                displayState = state.entityDisplayState,
                selectedEntityId = state.selectedEntityId,
                onSelectionChanged = onEntitySelected,
                addButtonText = stringResource(commonR.string.todo_widget_select_list),
                modifier = Modifier.formControlWidth(),
            )
            ConfigurationSections(
                state = state,
                onShowCompletedChanged = onShowCompletedChanged,
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
 * Everything describing how to display the selected list, revealed once there is one.
 */
@Composable
private fun ColumnScope.ConfigurationSections(
    state: TodoWidgetConfigureState,
    onShowCompletedChanged: (Boolean) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    AnimatedVisibility(visible = state.showConfiguration) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            ShowCompletedRow(
                checked = state.showCompleted,
                onCheckedChange = onShowCompletedChanged,
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
    )
}

@Composable
private fun ShowCompletedRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .formControlWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(commonR.string.widget_todo_show_completed),
            style = HATextStyle.Body,
            modifier = Modifier.weight(1f),
        )
        HASwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
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

@Preview
@Composable
private fun TodoWidgetConfigureContentPreview() {
    HAThemeForPreview {
        TodoWidgetConfigureContent(
            state = previewTodoWidgetConfigureState,
            snackbarHostState = remember { SnackbarHostState() },
            canNavigateBack = false,
            onNavigate = {},
            onServerSelected = {},
            onEntitySelected = {},
            onShowCompletedChanged = {},
            onBackgroundTypeSelected = {},
            onTextColorSelected = {},
            onActionClick = {},
        )
    }
}

private val previewTodoWidgetConfigureState = TodoWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    entityDisplayState = EntityDisplayState.Loaded(
        listOf(
            EntityDisplayWithContext(
                item = EntityDisplayWithoutContext(
                    entityId = "todo.shopping_list",
                    name = "Shopping List",
                    icon = CommunityMaterial.Icon.cmd_clipboard_list,
                ),
                areaName = "Kitchen",
            ),
        ),
    ),
    selectedEntityId = "todo.shopping_list",
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
