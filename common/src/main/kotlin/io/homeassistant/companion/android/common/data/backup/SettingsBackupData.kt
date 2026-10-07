package io.homeassistant.companion.android.common.data.backup

import kotlin.time.Instant
import kotlinx.serialization.Serializable

internal const val SETTINGS_BACKUP_FORMAT = "home-assistant-companion-settings"
internal const val SETTINGS_BACKUP_VERSION = 1
const val SETTINGS_BACKUP_MAX_BYTES = 1_048_576

/** A portable configuration document. A missing section leaves destination settings unchanged. */
@Serializable
data class SettingsBackupData(
    val format: String,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Instant,
    val servers: List<BackupServerData>,
    val favorites: List<BackupEntityReference>? = null,
    val sensorOptions: List<BackupSensorOptionsData>? = null,
    val sensorUpdateFrequency: String? = null,
)

/** [reference] belongs to this document, never to the source installation's database. */
@Serializable
data class BackupServerData(
    val reference: String,
    val name: String,
    val sensors: Map<String, Boolean>? = null,
    val persistentConnection: String? = null,
)

@Serializable
data class BackupEntityReference(val server: String, val entityId: String)

/** Only declared, portable sensor options are exported; runtime and identity settings are excluded. */
@Serializable
data class BackupSensorOptionsData(val sensor: String, val options: Map<String, BackupSensorOptionData>)

/** Zone selections use portable references; other supported options use their existing setting value. */
@Serializable
data class BackupSensorOptionData(
    val value: String? = null,
    val zones: List<BackupEntityReference>? = null,
    val enabled: Boolean,
)

/** Independently selectable sections for exporting or restoring settings. */
data class BackupSections(
    val favorites: Boolean = true,
    val sensors: Boolean = true,
    val sensorOptions: Boolean = true,
    val connection: Boolean = true,
    val frequency: Boolean = true,
) {
    val hasSelection: Boolean = favorites || sensors || sensorOptions || connection || frequency
}

/** Retains only the selected sections, preserving server references and favorite ordering. */
fun SettingsBackupData.select(sections: BackupSections): SettingsBackupData = copy(
    servers = servers.map {
        it.copy(
            sensors = it.sensors.takeIf { sections.sensors },
            persistentConnection = it.persistentConnection.takeIf { sections.connection },
        )
    },
    favorites = favorites.takeIf { sections.favorites },
    sensorOptions = sensorOptions.takeIf { sections.sensorOptions },
    sensorUpdateFrequency = sensorUpdateFrequency.takeIf { sections.frequency },
)
