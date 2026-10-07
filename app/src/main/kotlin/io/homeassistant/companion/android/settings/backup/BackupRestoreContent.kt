package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
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
            when {
                state?.restore == null -> BackupInsetContent {
                    Text(
                        stringResource(R.string.backup_select_again),
                        style = HATextStyle.Body,
                        textAlign = TextAlign.Start,
                    )
                }
                summary == null -> BackupRestoreSelection(state, onSelect, onMapServer, onReview)
                else -> BackupSettingsSection(R.string.backup_confirm_title) {
                    BackupDestinationSummary(state.restoreServers?.rows.orEmpty())
                    BackupRestoreSummary(summary)
                    HAAccentButton(
                        stringResource(R.string.backup_apply),
                        onRestore,
                        enabled = !state.busy && summary.hasChanges,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            BackupInsetContent {
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
}

@Composable
private fun ColumnScope.BackupRestoreSelection(
    state: SettingsBackupUiState.Content,
    onSelect: (BackupSection, Boolean) -> Unit,
    onMapServer: (String, BackupServerTarget) -> Unit,
    onReview: () -> Unit,
) {
    BackupSettingsSection(R.string.backup_choose_settings) {
        BackupSectionChoices(state.sectionRows, !state.busy, onSelect)
    }
    BackupServerChoices(state.restoreServers, state.servers, !state.busy, onMapServer)
    BackupInsetContent {
        HAAccentButton(
            stringResource(R.string.backup_review),
            onReview,
            enabled = state.canReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
