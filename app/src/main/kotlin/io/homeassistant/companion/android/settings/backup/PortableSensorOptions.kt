package io.homeassistant.companion.android.settings.backup

import io.homeassistant.companion.android.common.data.backup.BackupEntityReference
import io.homeassistant.companion.android.common.data.backup.BackupSensorOptionData
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.database.sensor.SensorSetting

/** Text settings currently contain the BLE transmitter's device identity and are deliberately local. */
internal fun Setting.isPortable(): Boolean = this !is Setting.Text

internal fun exportSensorOption(
    definition: Setting,
    setting: SensorSetting,
    references: Map<Int, String>,
): BackupSensorOptionData? {
    // Numeric readers fall back to the declared default without replacing the stored text.
    val value = when (definition) {
        is Setting.Number -> setting.value.takeIf { it.toIntOrNull() != null } ?: definition.defaultValue
        is Setting.Decimal -> setting.value.takeIf { it.toDoubleOrNull() != null } ?: definition.defaultValue
        else -> setting.value
    }
    return when {
        !definition.isPortable() -> null
        definition is Setting.Zones -> exportZones(value, references)?.let {
            BackupSensorOptionData(zones = it, enabled = setting.enabled)
        }
        else -> BackupSensorOptionData(value = value, enabled = setting.enabled)
    }
}

private fun exportZones(value: String, references: Map<Int, String>): List<BackupEntityReference>? {
    val zones = value.split(", ").filter { it.isNotBlank() }.map { entry ->
        val parts = entry.split("_", limit = 2)
        val reference = parts.first().toIntOrNull()?.let(references::get)
        if (reference != null && parts.size == 2) BackupEntityReference(reference, parts.last()) else null
    }
    return zones.filterNotNull().takeIf { it.size == zones.size }
}

internal fun restoreSensorOption(
    sensorId: String,
    definition: Setting,
    option: BackupSensorOptionData,
    servers: Map<String, Int>,
): SensorSetting? {
    val value = if (definition is Setting.Zones) {
        option.zones?.takeIf { zones -> zones.all { it.server in servers } }
            ?.joinToString { "${servers.getValue(it.server)}_${it.entityId}" }
    } else {
        option.value?.takeIf { definition.isPortable() && definition.accepts(it) }
    } ?: return null
    return SensorSetting(
        sensorId = sensorId,
        name = definition.name,
        value = value,
        valueType = definition.type,
        enabled = option.enabled,
        entries = if (definition is Setting.Options) definition.entries else emptyList(),
    )
}

private fun Setting.accepts(value: String): Boolean = when (this) {
    is Setting.Toggle -> value.toBooleanStrictOrNull() != null
    is Setting.Number -> value.toIntOrNull() != null
    is Setting.Decimal -> value.toDoubleOrNull()?.isFinite() == true
    is Setting.Options -> value in entries
    is Setting.Apps, is Setting.BluetoothDevices, is Setting.Beacons -> true
    is Setting.Text, is Setting.Zones -> false
}
