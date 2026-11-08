package io.homeassistant.companion.android.widgets.configure

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

/**
 * Shared layout for every widget configuration screen: a top bar, a scrolling centered column with
 * the server selector at the top and the action button at the bottom, and the screen's own controls
 * in between via [content].
 *
 * @param title Title shown in the top bar.
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 * @param onNavigate Invoked when the back arrow or close button is pressed.
 */
@Composable
internal fun WidgetConfigureScaffold(
    @StringRes title: Int,
    state: WidgetConfigureState<*>,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onServerSelected: (Int) -> Unit,
    onActionClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(title)) },
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
            WidgetServerSelector(
                items = state.serversDropdownItems,
                selectedServerId = state.selectedServerId,
                showServerSelector = state.showServerSelector,
                onServerSelected = onServerSelected,
            )

            content()

            HAAccentButton(
                text = stringResource(state.actionButtonLabel),
                onClick = onActionClick,
                modifier = Modifier.formControlWidth(),
                enabled = state.isActionEnabled,
            )
        }
    }
}

@Composable
private fun WidgetServerSelector(
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

/** Caps form controls to a readable width on large screens while filling narrow ones. */
internal fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()
