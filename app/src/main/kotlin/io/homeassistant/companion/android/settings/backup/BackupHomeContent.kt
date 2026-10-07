package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
internal fun BackupHomeContent(
    state: SettingsBackupUiState.Content,
    onSelect: (BackupSection, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onManageSensors: () -> Unit,
) {
    BackupColumn {
        BackupSettingsSection(R.string.backup_choose_export) {
            BackupSectionChoices(state.sectionRows, enabled = !state.busy, onSelect)
            HAAccentButton(
                text = stringResource(R.string.backup_export),
                onClick = onExport,
                enabled = !state.busy && state.sections.hasSelection,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HAHorizontalDivider()
        BackupSettingsSection(R.string.backup_restore_heading) {
            Text(
                stringResource(R.string.backup_import_description),
                style = HATextStyle.BodyMedium,
                textAlign = TextAlign.Start,
            )
            HAFilledButton(
                text = stringResource(R.string.backup_import),
                onClick = onImport,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.lastSummary?.let { summary ->
            HAHorizontalDivider()
            BackupSettingsSection(R.string.backup_restore_success) {
                BackupRestoreSummary(summary)
                HAFilledButton(stringResource(R.string.sensor_title), onClick = onManageSensors)
            }
        }
        BackupInsetContent {
            if (state.busy) HALoading()
            HAHint(stringResource(R.string.backup_privacy))
        }
    }
}

@Composable
internal fun BackupColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(safeBottomPaddingValues()).padding(vertical = HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        content = content,
    )
}

@Composable
internal fun BackupErrorContent(onRetry: () -> Unit) {
    BackupColumn {
        BackupInsetContent {
            Text(
                stringResource(R.string.backup_operation_failed),
                style = HATextStyle.Body,
                textAlign = TextAlign.Start,
            )
            HAFilledButton(stringResource(R.string.retry), onClick = onRetry)
        }
    }
}

@Preview
@Composable
private fun BackupHomePreview() {
    HAThemeForPreview {
        BackupHomeContent(SettingsBackupUiState.Content(emptyList()), { _, _ -> }, {}, {}, {})
    }
}
