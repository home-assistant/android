package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData

internal sealed class BackupSection(@StringRes val label: Int) {
    data object Favorites : BackupSection(R.string.backup_section_favorites)
    data object Sensors : BackupSection(R.string.backup_section_sensors)
    data object SensorOptions : BackupSection(R.string.backup_section_sensor_options)
    data object Connection : BackupSection(R.string.backup_section_connection)
    data object Frequency : BackupSection(R.string.sensor_update_frequency)
}

internal data class BackupSectionSelection(
    val section: BackupSection,
    val selected: Boolean,
    val available: Boolean = true,
)

internal data class RestoreDraft(
    val backup: SettingsBackupData,
    val mapping: Map<String, Int> = emptyMap(),
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
        val mappingValid: Boolean = restore?.mapping?.values?.let { it.distinct().size == it.size } != false
        val canReview: Boolean = !busy && mappingValid && sectionRows.any { it.available && it.selected }
    }
}

internal sealed interface BackupEvent {
    data object ShowRestore : BackupEvent
    data object Restored : BackupEvent
    data class Message(@StringRes val message: Int) : BackupEvent
}

internal fun sectionSelections(sections: BackupSections, backup: SettingsBackupData? = null) = listOf(
    BackupSectionSelection(BackupSection.Favorites, sections.favorites, backup == null || backup.favorites != null),
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
)
