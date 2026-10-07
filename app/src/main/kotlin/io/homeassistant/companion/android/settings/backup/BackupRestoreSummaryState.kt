package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting

internal sealed interface BackupSummaryValue {
    data class Count(val count: Int) : BackupSummaryValue
    data class Sensors(val enabled: Int, val disabled: Int) : BackupSummaryValue
    data class Servers(val count: Int) : BackupSummaryValue
    data class Frequency(@StringRes val label: Int) : BackupSummaryValue
}

internal data class BackupSummaryRow(
    val section: BackupSection,
    val value: BackupSummaryValue,
    @StringRes val note: Int? = null,
)

internal data class BackupRestoreSummaryState(
    val rows: List<BackupSummaryRow>,
    val issues: List<RestoreIssue>,
    val hasChanges: Boolean,
)

/** Only include categories that will actually be written; an empty favorites list explicitly clears it. */
internal fun backupRestoreSummary(plan: SettingsRestorePlan): BackupRestoreSummaryState {
    val changes = plan.changes
    val rows = buildList {
        plan.favorites?.let {
            add(
                BackupSummaryRow(
                    BackupSection.AndroidAutoFavorites,
                    BackupSummaryValue.Count(it.size),
                    if (it.isEmpty()) R.string.backup_favorites_clear else R.string.backup_favorites_replace,
                ),
            )
        }
        if (changes.sensors.isNotEmpty()) {
            val enabled = changes.sensors.count { it.enabled }
            add(
                BackupSummaryRow(
                    BackupSection.Sensors,
                    BackupSummaryValue.Sensors(
                        enabled,
                        changes.sensors.size - enabled,
                    ),
                ),
            )
        }
        if (changes.options.isNotEmpty()) {
            add(
                BackupSummaryRow(
                    BackupSection.SensorOptions,
                    BackupSummaryValue.Count(changes.options.size),
                    R.string.backup_shared_settings,
                ),
            )
        }
        if (changes.connections.isNotEmpty()) {
            add(BackupSummaryRow(BackupSection.Connection, BackupSummaryValue.Servers(changes.connections.size)))
        }
        changes.frequency?.let {
            val label = when (it) {
                SensorUpdateFrequencySetting.NORMAL -> R.string.backup_frequency_normal
                SensorUpdateFrequencySetting.FAST_WHILE_CHARGING -> R.string.backup_frequency_charging
                SensorUpdateFrequencySetting.FAST_ALWAYS -> R.string.backup_frequency_fast
            }
            add(
                BackupSummaryRow(
                    BackupSection.Frequency,
                    BackupSummaryValue.Frequency(label),
                    R.string.backup_frequency_restart,
                ),
            )
        }
    }
    return BackupRestoreSummaryState(rows, plan.issues, plan.changeCount > 0)
}
