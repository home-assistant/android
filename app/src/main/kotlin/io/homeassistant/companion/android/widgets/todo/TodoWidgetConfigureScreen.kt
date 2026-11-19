package io.homeassistant.companion.android.widgets.todo

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ClipboardList
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.configure.WidgetAppearanceSection
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScaffold
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScreen
import io.homeassistant.companion.android.widgets.configure.formControlWidth

/**
 * Configuration screen of the to-do widget, bound to its [TodoWidgetConfigureViewModel].
 *
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 */
@SuppressLint("ComposeViewModelForwarding")
@Composable
internal fun TodoWidgetConfigureScreen(
    viewModel: TodoWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    WidgetConfigureScreen(viewModel) { state, snackbarHostState ->
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
    WidgetConfigureScaffold(
        title = commonR.string.widget_todo_label,
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = onServerSelected,
        onActionClick = onActionClick,
    ) {
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
            WidgetAppearanceSection(
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
private fun ShowCompletedRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // Shared with the switch so presses anywhere on the row drive its press animation.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .formControlWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                indication = null,
                interactionSource = interactionSource,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(commonR.string.widget_todo_show_completed),
            style = HATextStyle.Body,
            color = LocalHAColorScheme.current.colorTextPrimary,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        HASwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            interactionSource = interactionSource,
        )
    }
}

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
                    icon = Mdi.ClipboardList,
                ),
                areaName = "Kitchen",
            ),
        ),
    ),
    selectedEntityId = "todo.shopping_list",
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
