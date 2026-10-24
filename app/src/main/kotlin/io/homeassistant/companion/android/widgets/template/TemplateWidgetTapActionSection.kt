package io.homeassistant.companion.android.widgets.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.dashboards.DashboardTitle
import io.homeassistant.companion.android.database.widget.WidgetTapAction

/** Picks what tapping the widget does, and the dashboard to open when relevant. */
@Composable
internal fun TapActionSection(
    selectedOption: WidgetTapAction,
    dashboards: DashboardsState,
    selectedDashboardUrlPath: String?,
    onOptionSelected: (WidgetTapAction) -> Unit,
    onDashboardSelected: (urlPath: String) -> Unit,
    onRetryLoadDashboards: () -> Unit,
) {
    TapActionDropdown(selectedOption = selectedOption, onOptionSelected = onOptionSelected)
    if (selectedOption == WidgetTapAction.OPEN_DASHBOARD) {
        DashboardDropdown(
            dashboards = dashboards,
            selectedUrlPath = selectedDashboardUrlPath,
            onDashboardSelected = onDashboardSelected,
        )
        if (dashboards == DashboardsState.Error) DashboardsError(onRetry = onRetryLoadDashboards)
    }
}

@Composable
private fun DashboardsError(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        modifier = Modifier.formControlWidth(),
    ) {
        Text(
            text = stringResource(commonR.string.widget_tap_action_dashboards_error),
            style = HATextStyle.BodyMedium,
            color = LocalHAColorScheme.current.colorTextSecondary,
            modifier = Modifier.weight(1f),
        )
        HAPlainButton(text = stringResource(commonR.string.retry), onClick = onRetry)
    }
}

@Composable
private fun TapActionDropdown(selectedOption: WidgetTapAction, onOptionSelected: (WidgetTapAction) -> Unit) {
    val refreshLabel = stringResource(commonR.string.refresh)
    val openDashboardLabel = stringResource(commonR.string.widget_tap_action_open_dashboard)
    val items = remember(refreshLabel, openDashboardLabel) {
        listOf(
            HADropdownItem<WidgetTapAction>(key = WidgetTapAction.REFRESH, label = refreshLabel),
            HADropdownItem<WidgetTapAction>(key = WidgetTapAction.OPEN_DASHBOARD, label = openDashboardLabel),
        )
    }

    HADropdownMenu(
        items = items,
        selectedKey = selectedOption,
        onItemSelected = onOptionSelected,
        label = stringResource(commonR.string.widget_tap_action_label),
        modifier = Modifier.formControlWidth(),
    )
}

@Composable
private fun DashboardDropdown(
    dashboards: DashboardsState,
    selectedUrlPath: String?,
    onDashboardSelected: (urlPath: String) -> Unit,
) {
    val resources = LocalResources.current
    val items = remember(dashboards, resources) {
        (dashboards as? DashboardsState.Loaded)?.dashboards.orEmpty().map { dashboard ->
            val title = when (val title = dashboard.title) {
                is DashboardTitle.Text -> title.value
                is DashboardTitle.Resource -> resources.getString(title.resId)
            }
            HADropdownItem(key = dashboard.urlPath, label = title)
        }
    }

    HADropdownMenu(
        items = items,
        selectedKey = selectedUrlPath,
        onItemSelected = onDashboardSelected,
        label = stringResource(commonR.string.widget_tap_action_dashboard_label),
        placeholder = if (dashboards == DashboardsState.Loading) {
            stringResource(commonR.string.widget_tap_action_dashboards_loading)
        } else {
            null
        },
        enabled = dashboards is DashboardsState.Loaded,
        modifier = Modifier.formControlWidth(),
    )
}

private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()
