package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
internal fun BackupRestoreContent(
    state: SettingsBackupUiState.Content?,
    onSelect: (BackupSection, Boolean) -> Unit,
    onMapServer: (String, Int?) -> Unit,
    onReview: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    BackupColumn {
        Text(stringResource(R.string.backup_review), style = HATextStyle.Headline)
        if (state?.restore == null) {
            Text(stringResource(R.string.backup_select_again), style = HATextStyle.Body)
        } else {
            BackupRestoreSelection(state, onSelect, onMapServer)
            if (state.restore.plan == null) {
                HAAccentButton(
                    stringResource(R.string.backup_review),
                    onReview,
                    enabled = state.canReview,
                )
            } else {
                HAHint(stringResource(R.string.backup_restore_effects))
                BackupRestoreSummary(state.restore.plan)
                HAAccentButton(
                    stringResource(R.string.backup_apply),
                    onRestore,
                    enabled = !state.busy && state.restore.plan.changeCount > 0,
                )
            }
            if (state.busy) HALoading()
        }
        HAFilledButton(stringResource(R.string.cancel), onCancel, enabled = state?.busy != true)
    }
}

@Composable
private fun BackupRestoreSelection(
    state: SettingsBackupUiState.Content,
    onSelect: (BackupSection, Boolean) -> Unit,
    onMapServer: (String, Int?) -> Unit,
) {
    val restore = state.restore ?: return
    val skip = stringResource(R.string.backup_skip_server)
    val destinations = remember(state.servers, skip) {
        listOf(HADropdownItem<Int?>(null, skip)) + state.servers.map { HADropdownItem<Int?>(it.id, it.name) }
    }
    Text(
        stringResource(R.string.backup_source, restore.backup.appVersion, restore.backup.createdAt.toString()),
        style = HATextStyle.Body,
    )
    HAHint(stringResource(R.string.backup_restore_description))
    restore.backup.servers.forEach { server ->
        HADropdownMenu(
            items = destinations,
            selectedKey = restore.mapping[server.reference],
            onItemSelected = { onMapServer(server.reference, it) },
            label = server.name,
            placeholder = skip,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (!state.mappingValid) HAHint(stringResource(R.string.backup_duplicate_destination))
    BackupSectionChoices(state.sectionRows, enabled = !state.busy, onSelect)
}

@Composable
internal fun BackupRestoreSummary(plan: SettingsRestorePlan) {
    Text(
        stringResource(
            R.string.backup_restore_counts,
            plan.favorites?.size ?: 0,
            plan.changes.sensors.size,
            plan.changes.options.size,
            plan.changes.connections.size,
        ),
        style = HATextStyle.Body,
    )
    if (plan.changes.frequency !=
        null
    ) {
        Text(stringResource(R.string.backup_frequency_included), style = HATextStyle.Body)
    }
    if (plan.changeCount == 0) Text(stringResource(R.string.backup_nothing_to_restore), style = HATextStyle.Body)
    plan.issues.forEach { issue ->
        val message = when (issue) {
            is RestoreIssue.UnsupportedSensor -> R.string.backup_unsupported_sensor
            is RestoreIssue.PermissionRequired -> R.string.backup_permission_required
            is RestoreIssue.UnsupportedOption -> R.string.backup_unsupported_option
        }
        Text(stringResource(message, issue.identifier), style = HATextStyle.Body)
    }
}
