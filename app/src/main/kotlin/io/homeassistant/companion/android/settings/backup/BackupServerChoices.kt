package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
internal fun ColumnScope.BackupServerChoices(
    selections: BackupServerSelections?,
    servers: List<BackupDestination>,
    enabled: Boolean,
    onMapServer: (String, BackupServerTarget) -> Unit,
) {
    if (selections == null || selections.rows.isEmpty()) return
    val destinations = remember(servers, enabled) {
        servers.map { RadioOption(selectionKey = it.id, headline = it.name, enabled = enabled) }
    }
    HAHorizontalDivider()
    BackupSettingsSection(commonR.string.backup_servers_title) {
        Text(
            stringResource(commonR.string.backup_restore_description),
            style = HATextStyle.BodyMedium,
            textAlign = TextAlign.Start,
        )
        selections.rows.forEach { server ->
            BackupServerChoice(server, selections.canExcludeServers, destinations, enabled) {
                onMapServer(server.reference, it)
            }
        }
        selections.problem?.let {
            Text(stringResource(it), style = HATextStyle.BodyMedium, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun ColumnScope.BackupServerChoice(
    server: BackupServerSelection,
    canExclude: Boolean,
    destinations: List<RadioOption<Int>>,
    enabled: Boolean,
    onSelect: (BackupServerTarget) -> Unit,
) {
    val title = stringResource(commonR.string.backup_restore_server, server.name)
    if (canExclude) {
        BackupSwitchRow(
            title = title,
            description = stringResource(
                if (server.included) commonR.string.backup_choose_server else commonR.string.backup_server_excluded,
            ),
            icon = commonR.drawable.ic_stat_ic_notification_blue,
            selected = server.included,
            enabled = enabled,
            onSelect = { onSelect(if (it) BackupServerTarget.Unselected else BackupServerTarget.Skip) },
        )
    } else {
        BackupPreferenceRow(title, null, commonR.drawable.ic_stat_ic_notification_blue)
    }
    if (!canExclude || server.included) {
        HARadioGroup(
            options = destinations,
            selectionKey = (server.target as? BackupServerTarget.Server)?.id,
            onSelect = { onSelect(BackupServerTarget.Server(it.selectionKey)) },
            modifier = Modifier.fillMaxWidth(),
            spaceBy = HADimens.SPACE2,
        )
    }
}
