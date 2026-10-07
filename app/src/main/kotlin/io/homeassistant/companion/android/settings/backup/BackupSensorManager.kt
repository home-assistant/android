package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupSensorOptionsData
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Supplies the available sensor definitions and their portable configuration. */
internal class BackupSensorManager @Inject constructor(
    private val managers: Set<@JvmSuppressWildcards SensorManager>,
    private val repository: SensorRepository,
) {
    suspend fun capabilities(): Map<String, BackupSensorCapability> = buildMap {
        managers.filter { it.hasSensor() }.forEach { manager ->
            manager.getAvailableSensors().forEach { sensor ->
                put(sensor.id, BackupSensorCapability(sensor, manager.checkPermission(sensor.id)))
            }
        }
    }

    suspend fun selections() = repository.getAllFlow().first().groupBy { it.serverId }

    suspend fun options(
        capabilities: Map<String, BackupSensorCapability>,
        references: Map<Int, String>,
    ): List<BackupSensorOptionsData> = capabilities.values.mapNotNull { capability ->
        val sensor = capability.definition
        val current = repository.getSettings(sensor.id).associateBy { it.name }
        val options = sensor.settings.mapNotNull { definition ->
            current[definition.name]?.let { exportSensorOption(definition, it, references) }
                ?.let { definition.name to it }
        }.toMap()
        options.takeIf { it.isNotEmpty() }?.let { BackupSensorOptionsData(sensor.id, it) }
    }
}
