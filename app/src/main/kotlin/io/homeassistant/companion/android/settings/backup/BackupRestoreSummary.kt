package io.homeassistant.companion.android.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
internal fun ColumnScope.BackupRestoreSummary(summary: BackupRestoreSummaryState) {
    summary.rows.forEach { row ->
        BackupSummaryRow(row)
        HAHorizontalDivider()
    }
    if (!summary.hasChanges) {
        Text(
            stringResource(R.string.backup_nothing_to_restore),
            style = HATextStyle.Body,
            textAlign = TextAlign.Start,
        )
    }
    if (summary.issues.isNotEmpty()) {
        Text(
            stringResource(R.string.backup_not_restored),
            style = HATextStyle.HeadlineMedium,
            textAlign = TextAlign.Start,
        )
        summary.issues.forEach { issue ->
            val message = when (issue) {
                is RestoreIssue.UnsupportedSensor -> R.string.backup_unsupported_sensor
                is RestoreIssue.PermissionRequired -> R.string.backup_permission_required
                is RestoreIssue.UnsupportedOption -> R.string.backup_unsupported_option
            }
            Text(stringResource(message, issue.identifier), style = HATextStyle.Body, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun BackupSummaryRow(row: BackupSummaryRow) {
    val value = when (val value = row.value) {
        is BackupSummaryValue.Count -> value.count.toString()
        is BackupSummaryValue.Sensors -> stringResource(R.string.backup_sensor_counts, value.enabled, value.disabled)
        is BackupSummaryValue.Servers -> pluralStringResource(R.plurals.backup_server_count, value.count, value.count)
        is BackupSummaryValue.Frequency -> stringResource(value.label)
    }
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(row.section.label),
                modifier = Modifier.weight(1f),
                style = HATextStyle.Body,
                textAlign = TextAlign.Start,
            )
            Text(value, modifier = Modifier.weight(1f), style = HATextStyle.Body, textAlign = TextAlign.End)
        }
        row.note?.let { Text(stringResource(it), style = HATextStyle.BodyMedium, textAlign = TextAlign.Start) }
    }
}
