package io.homeassistant.companion.android.database.settings

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.homeassistant.companion.android.common.data.backup.BackupSettingsChanges
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting

private const val APP_SETTINGS_ID = 0

@Dao
internal interface SettingsBackupDao {
    @Query("SELECT * FROM settings")
    suspend fun getSettings(): List<Setting>

    @Query("SELECT id FROM servers")
    suspend fun getServerIds(): List<Int>

    @Query("SELECT * FROM sensors WHERE id = :id AND server_id = :serverId")
    suspend fun getSensor(id: String, serverId: Int): Sensor?

    @Upsert
    suspend fun saveSensor(sensor: Sensor)

    @Upsert
    suspend fun saveOptions(options: List<SensorSetting>)

    @Upsert
    suspend fun saveSetting(setting: Setting)

    @Transaction
    suspend fun apply(changes: BackupSettingsChanges) {
        check(getServerIds().containsAll(changes.serverIds)) { "A destination server was removed" }
        changes.sensors.forEach { selection ->
            val sensor = getSensor(selection.sensorId, selection.serverId)
                ?: Sensor(selection.sensorId, selection.serverId, enabled = false, state = "")
            saveSensor(sensor.copy(enabled = selection.enabled, lastSentState = null, lastSentIcon = null))
        }
        saveOptions(changes.options)
        val settings = getSettings().associateBy { it.id }
        changes.connections.forEach { (id, connection) ->
            val current = settings[id] ?: defaultSetting(id)
            saveSetting(current.copy(websocketSetting = connection))
        }
        changes.frequency?.let {
            val current = settings[APP_SETTINGS_ID] ?: defaultSetting(APP_SETTINGS_ID)
            saveSetting(current.copy(sensorUpdateFrequency = it))
        }
    }

    private fun defaultSetting(id: Int) = Setting(id, WebsocketSetting.NEVER, SensorUpdateFrequencySetting.NORMAL)
}
