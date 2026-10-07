package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
internal fun BackupRestoreContent(
    state: SettingsBackupUiState.Content?,
    onSelect: (BackupSection, Boolean) -> Unit,
    onMapServer: (String, BackupServerTarget) -> Unit,
    onReview: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    // A separate scroll position for confirmation keeps its summary visible when continuing.
    key(state?.reviewSummary != null) {
        BackupColumn {
            val summary = state?.reviewSummary
            val title = if (summary == null) R.string.backup_choose_settings else R.string.backup_confirm_title
            Text(stringResource(title), style = HATextStyle.HeadlineMedium, textAlign = TextAlign.Start)
            when {
                state?.restore == null -> Text(
                    stringResource(R.string.backup_select_again),
                    style = HATextStyle.Body,
                    textAlign = TextAlign.Start,
                )
                summary == null -> {
                    BackupSectionChoices(state.sectionRows, !state.busy, onSelect)
                    BackupServerChoices(state.restoreServers, state.servers, !state.busy, onMapServer)
                    HAAccentButton(
                        stringResource(R.string.backup_review),
                        onReview,
                        enabled = state.canReview,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    BackupDestinationSummary(state.restoreServers?.rows.orEmpty())
                    BackupRestoreSummary(summary)
                    HAAccentButton(
                        stringResource(R.string.backup_apply),
                        onRestore,
                        enabled =
                        !state.busy && summary.hasChanges,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state?.busy == true) HALoading()
            val action = if (summary == null) onCancel else onEdit
            val label = if (summary == null) R.string.cancel else R.string.backup_edit_selection
            HAFilledButton(
                stringResource(label),
                action,
                enabled = state?.busy != true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.BackupServerChoices(
    selections: BackupServerSelections?,
    servers: List<BackupDestination>,
    enabled: Boolean,
    onMapServer: (String, BackupServerTarget) -> Unit,
) {
    if (selections == null || selections.rows.isEmpty()) return
    val skip = stringResource(R.string.backup_skip_server)
    val choose = stringResource(R.string.backup_choose_server)
    val destinations = remember(servers, skip, choose) {
        listOf(HADropdownItem<BackupServerTarget>(BackupServerTarget.Unselected, choose)) +
            servers.map { HADropdownItem<BackupServerTarget>(BackupServerTarget.Server(it.id), it.name) } +
            HADropdownItem<BackupServerTarget>(BackupServerTarget.Skip, skip)
    }
    Text(stringResource(R.string.backup_servers_title), style = HATextStyle.HeadlineMedium, textAlign = TextAlign.Start)
    Text(stringResource(R.string.backup_restore_description), style = HATextStyle.Body, textAlign = TextAlign.Start)
    selections.rows.forEach { server ->
        HADropdownMenu(
            items = destinations,
            selectedKey = server.target,
            onItemSelected = { onMapServer(server.reference, it) },
            label = stringResource(R.string.backup_server_destination, server.name),
            placeholder = choose,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    selections.problem?.let { Text(stringResource(it), style = HATextStyle.BodyMedium, textAlign = TextAlign.Start) }
}

@Composable
private fun ColumnScope.BackupDestinationSummary(servers: List<BackupServerSelection>) {
    servers.forEach { server ->
        val destination = server.destinationName
        val text = if (destination == null) {
            stringResource(R.string.backup_server_skipped, server.name)
        } else {
            stringResource(R.string.backup_restore_destination, server.name, destination)
        }
        Text(text, style = HATextStyle.Body, textAlign = TextAlign.Start)
    }
}
