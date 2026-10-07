package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.common.data.backup.RestoredSensorSelection
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.WebsocketSetting

internal data class BackupDestination(val id: Int, val name: String)

internal data class BackupSensorCapability(val definition: SensorManager.BasicSensor, val hasPermission: Boolean)

internal sealed interface RestoreIssue {
    val identifier: String
    data class UnsupportedSensor(override val identifier: String) : RestoreIssue
    data class PermissionRequired(override val identifier: String) : RestoreIssue
    data class UnsupportedOption(override val identifier: String) : RestoreIssue
}

internal data class SettingsRestorePlan(
    val changes: BackupSettingsChanges,
    val favorites: List<AutoFavorite>?,
    val issues: List<RestoreIssue>,
) {
    val changeCount: Int = changes.sensors.size + changes.options.size + changes.connections.size +
        (if (changes.frequency != null) 1 else 0) + (if (favorites != null) 1 else 0)
}

/** Resolves document references and validates capabilities without writing or contacting a server. */
internal fun prepareSettingsRestore(
    backup: SettingsBackupData,
    mapping: Map<String, Int>,
    destinations: List<BackupDestination>,
    capabilities: Map<String, BackupSensorCapability>,
): SettingsRestorePlan {
    require(mapping.values.distinct().size == mapping.size) { "Each destination can only be selected once" }
    require(mapping.keys.all { key -> backup.servers.any { it.reference == key } }) { "Unknown source server" }
    require(mapping.values.all { id -> destinations.any { it.id == id } }) { "Unknown destination server" }
    val issues = mutableListOf<RestoreIssue>()
    val sensors = restoreSensorSelections(backup, mapping, capabilities, issues)
    val options = restoreSensorOptions(backup, mapping, capabilities, issues)
    val connections = backup.servers.mapNotNull { server ->
        val id = mapping[server.reference] ?: return@mapNotNull null
        server.persistentConnection?.let { id to WebsocketSetting.valueOf(it) }
    }.toMap()
    return SettingsRestorePlan(
        changes = BackupSettingsChanges(
            serverIds = mapping.values.toSet(),
            sensors = sensors,
            options = options,
            connections = connections,
            frequency = backup.sensorUpdateFrequency?.let(SensorUpdateFrequencySetting::valueOf),
        ),
        favorites = backup.favorites?.takeIf { mapping.isNotEmpty() }?.mapNotNull { favorite ->
            mapping[favorite.server]?.let { AutoFavorite(it, favorite.entityId) }
        },
        issues = issues.distinct(),
    )
}

private fun restoreSensorSelections(
    backup: SettingsBackupData,
    mapping: Map<String, Int>,
    capabilities: Map<String, BackupSensorCapability>,
    issues: MutableList<RestoreIssue>,
): List<RestoredSensorSelection> = backup.servers.flatMap { server ->
    val id = mapping[server.reference] ?: return@flatMap emptyList()
    server.sensors.orEmpty().mapNotNull { (sensor, enabled) ->
        val capability = capabilities[sensor]
        when {
            capability == null -> {
                issues += RestoreIssue.UnsupportedSensor(sensor)
                null
            }
            enabled && !capability.hasPermission -> {
                issues += RestoreIssue.PermissionRequired(sensor)
                null
            }
            else -> RestoredSensorSelection(sensor, id, enabled)
        }
    }
}

private fun restoreSensorOptions(
    backup: SettingsBackupData,
    mapping: Map<String, Int>,
    capabilities: Map<String, BackupSensorCapability>,
    issues: MutableList<RestoreIssue>,
): List<SensorSetting> = backup.sensorOptions.orEmpty().flatMap { sensor ->
    val definitions = capabilities[sensor.sensor]?.definition?.settings.orEmpty().associateBy { it.name }
    sensor.options.mapNotNull { (name, option) ->
        val restored = definitions[name]?.let { restoreSensorOption(sensor.sensor, it, option, mapping) }
        if (restored == null) issues += RestoreIssue.UnsupportedOption("${sensor.sensor}/$name")
        restored
    }
}
