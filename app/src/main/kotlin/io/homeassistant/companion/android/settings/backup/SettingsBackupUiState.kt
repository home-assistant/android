package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.common.data.backup.select

internal sealed class BackupSection(@StringRes val label: Int, @StringRes val description: Int) {
    data object AndroidAutoFavorites : BackupSection(
        R.string.backup_section_favorites,
        R.string.backup_favorites_description,
    )
    data object Sensors : BackupSection(R.string.backup_section_sensors, R.string.backup_sensors_description)
    data object SensorOptions : BackupSection(
        R.string.backup_section_sensor_options,
        R.string.backup_sensor_options_description,
    )
    data object Connection : BackupSection(R.string.backup_section_connection, R.string.backup_connection_description)
    data object Frequency : BackupSection(R.string.sensor_update_frequency, R.string.backup_frequency_description)
}

internal data class BackupSectionSelection(
    val section: BackupSection,
    val selected: Boolean,
    val available: Boolean = true,
)

internal data class RestoreDraft(
    val backup: SettingsBackupData,
    val targets: Map<String, BackupServerTarget> = emptyMap(),
    val plan: SettingsRestorePlan? = null,
)

internal sealed interface SettingsBackupUiState {
    data object Loading : SettingsBackupUiState
    data object Error : SettingsBackupUiState
    data class Content(
        val servers: List<BackupDestination>,
        val sections: BackupSections = BackupSections(),
        val sectionRows: List<BackupSectionSelection> = sectionSelections(BackupSections()),
        val restore: RestoreDraft? = null,
        val lastRestore: SettingsRestorePlan? = null,
        val busy: Boolean = false,
    ) : SettingsBackupUiState {
        val restoreServers: BackupServerSelections? = restore?.let {
            backupServerSelections(it.backup.select(sections), it.targets, servers)
        }
        val canReview: Boolean =
            !busy && restoreServers?.problem == null && sectionRows.any { it.available && it.selected }
        val reviewSummary: BackupRestoreSummaryState? = restore?.plan?.let(::backupRestoreSummary)
        val lastSummary: BackupRestoreSummaryState? = lastRestore?.let(::backupRestoreSummary)
    }
}

internal sealed interface BackupEvent {
    data object ShowRestore : BackupEvent
    data object Restored : BackupEvent
    data class Message(@StringRes val message: Int) : BackupEvent
}

internal fun sectionSelections(sections: BackupSections, backup: SettingsBackupData? = null) = listOf(
    BackupSectionSelection(
        BackupSection.AndroidAutoFavorites,
        sections.favorites,
        backup == null || backup.favorites != null,
    ),
    BackupSectionSelection(
        BackupSection.Sensors,
        sections.sensors,
        backup == null || backup.servers.any { it.sensors != null },
    ),
    BackupSectionSelection(
        BackupSection.SensorOptions,
        sections.sensorOptions,
        backup == null || backup.sensorOptions != null,
    ),
    BackupSectionSelection(
        BackupSection.Connection,
        sections.connection,
        backup == null || backup.servers.any { it.persistentConnection != null },
    ),
    BackupSectionSelection(
        BackupSection.Frequency,
        sections.frequency,
        backup == null || backup.sensorUpdateFrequency != null,
    ),
).filter { it.available }
