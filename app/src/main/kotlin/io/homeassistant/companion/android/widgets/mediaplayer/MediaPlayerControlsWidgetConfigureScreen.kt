package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

/**
 * Stateless configuration screen for the Media Player Controls widget.
 *
 * All state is hoisted to the caller (the configure activity) so this composable can be
 * previewed and screenshot-tested in isolation.
 */
@Composable
internal fun MediaPlayerControlsWidgetConfigureScreen(
    servers: List<HADropdownItem<Int>>,
    selectedServerId: Int?,
    onServerSelected: (Int) -> Unit,
    entityId: String,
    onEntityIdChange: (String) -> Unit,
    showVolume: Boolean,
    onShowVolumeChange: (Boolean) -> Unit,
    showSkip: Boolean,
    onShowSkipChange: (Boolean) -> Unit,
    showSeek: Boolean,
    onShowSeekChange: (Boolean) -> Unit,
    showSource: Boolean,
    onShowSourceChange: (Boolean) -> Unit,
    widgetLabel: String,
    onWidgetLabelChange: (String) -> Unit,
    backgroundOptions: List<HADropdownItem<String>>,
    selectedBackgroundKey: String,
    onBackgroundSelected: (String) -> Unit,
    isUpdate: Boolean,
    onAddWidgetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets.safeDrawing) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(commonR.string.select_entity_to_display),
                style = HATextStyle.Headline,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            if (servers.size > 1) {
                HADropdownMenu(
                    items = servers,
                    selectedKey = selectedServerId,
                    onItemSelected = onServerSelected,
                    label = stringResource(commonR.string.widget_spinner_server),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }

            HATextField(
                value = entityId,
                onValueChange = onEntityIdChange,
                label = { Text(stringResource(commonR.string.label_entity_ids)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            LabeledCheckbox(
                checked = showVolume,
                onCheckedChange = onShowVolumeChange,
                label = stringResource(commonR.string.widget_media_show_volume),
            )

            LabeledCheckbox(
                checked = showSkip,
                onCheckedChange = onShowSkipChange,
                label = stringResource(commonR.string.widget_media_show_skip),
            )

            LabeledCheckbox(
                checked = showSeek,
                onCheckedChange = onShowSeekChange,
                label = stringResource(commonR.string.widget_media_show_seek),
            )

            LabeledCheckbox(
                checked = showSource,
                onCheckedChange = onShowSourceChange,
                label = stringResource(commonR.string.widget_media_show_source),
                modifier = Modifier.padding(bottom = 16.dp),
            )

            HATextField(
                value = widgetLabel,
                onValueChange = onWidgetLabelChange,
                label = { Text(stringResource(commonR.string.label_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            HADropdownMenu(
                items = backgroundOptions,
                selectedKey = selectedBackgroundKey,
                onItemSelected = onBackgroundSelected,
                label = stringResource(commonR.string.widget_background_type_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )

            HAAccentButton(
                text = stringResource(
                    if (isUpdate) commonR.string.update_widget else commonR.string.add_widget,
                ),
                onClick = onAddWidgetClick,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
    ) {
        Text(text = label, style = HATextStyle.Body)
        Spacer(Modifier.weight(1f))
        HACheckbox(
            checked = checked,
            onCheckedChange = null, // we handle click on row
        )
    }
}
