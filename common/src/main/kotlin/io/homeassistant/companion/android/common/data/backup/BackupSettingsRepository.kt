package io.homeassistant.companion.android.common.data.backup

import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsBackupDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import javax.inject.Inject

data class RestoredSensorSelection(val sensorId: String, val serverId: Int, val enabled: Boolean)

/** Validated settings and synchronization changes, never sensor readings or device registrations. */
data class BackupSettingsChanges(
    val serverIds: Set<Int>,
    val sensors: List<RestoredSensorSelection>,
    val options: List<SensorSetting>,
    val connections: Map<Int, WebsocketSetting>,
    val frequency: SensorUpdateFrequencySetting?,
)

/** Reads settings and applies database-backed configuration in a single transaction. */
class BackupSettingsRepository @Inject internal constructor(private val dao: SettingsBackupDao) {
    /** Returns configured connection and update preferences, including app-level preferences. */
    suspend fun getSettings(): List<Setting> = dao.getSettings()

    /** Applies validated changes atomically, failing if a mapped server has been removed. */
    suspend fun apply(changes: BackupSettingsChanges) = dao.apply(changes)
}
