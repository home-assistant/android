package io.homeassistant.companion.android.database.sensor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SensorDao {

    @Query("SELECT * FROM Sensors WHERE id = :id")
    suspend fun get(id: String): List<Sensor>

    @Query("SELECT * FROM Sensors WHERE id = :id AND server_id = :serverId")
    suspend fun get(id: String, serverId: Int): Sensor?

    @Query("SELECT * FROM sensors")
    fun getAllFlow(): Flow<List<Sensor>>

    @Query("SELECT * FROM sensors")
    suspend fun getAll(): List<Sensor>

    @Transaction
    @Query(
        "SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id",
    )
    suspend fun getFull(id: String): Map<Sensor, List<Attribute>>

    @Transaction
    @Query(
        "SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id AND sensors.server_id = :serverId",
    )
    suspend fun getFull(id: String, serverId: Int): Map<Sensor, List<Attribute>>

    @Transaction
    @Query(
        "SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id",
    )
    fun getFullFlow(id: String): Flow<Map<Sensor, List<Attribute>>>

    @Query("SELECT * FROM Sensors WHERE server_id = :serverId")
    suspend fun getAllServer(serverId: Int): List<Sensor>

    @Query("SELECT * FROM Sensors WHERE NOT(server_id IN (:serverIds))")
    suspend fun getAllExceptServer(serverIds: List<Int>): List<Sensor>

    @Transaction
    @Query("SELECT * FROM sensor_settings WHERE sensor_id = :id")
    suspend fun getSettings(id: String): List<SensorSetting>

    @Transaction
    @Query("SELECT * FROM sensor_settings WHERE sensor_id = :id ORDER BY sensor_id")
    fun getSettingsFlow(id: String): Flow<List<SensorSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(attribute: Attribute)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(attributes: List<Attribute>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(sensorSetting: SensorSetting)

    @Query("DELETE FROM sensors WHERE id = :sensorId AND server_id = :serverId")
    suspend fun removeSensor(sensorId: String, serverId: Int)

    @Transaction
    suspend fun removeServer(serverId: Int) {
        getAllServer(serverId).forEach {
            removeSensor(it.id, serverId)
            if (get(it.id).isEmpty()) {
                clearSettings(it.id)
                clearAttributes(it.id)
            }
        }
    }

    @Query("DELETE FROM sensor_settings WHERE sensor_id = :sensorId")
    suspend fun clearSettings(sensorId: String)

    @Query("DELETE FROM sensor_settings WHERE sensor_id = :sensorId AND name = :settingName")
    suspend fun removeSetting(sensorId: String, settingName: String)

    @Query("DELETE FROM sensor_settings WHERE sensor_id = :sensorId AND name IN (:settingNames)")
    suspend fun removeSettings(sensorId: String, settingNames: List<String>)

    @Upsert
    suspend fun upsert(sensor: Sensor)

    @Query("DELETE FROM sensor_attributes WHERE sensor_id = :sensorId")
    suspend fun clearAttributes(sensorId: String)

    @Transaction
    suspend fun replaceAllAttributes(sensorId: String, attributes: List<Attribute>) {
        clearAttributes(sensorId)
        add(attributes)
    }

    @Query("UPDATE sensor_settings SET enabled = :enabled WHERE sensor_id = :sensorId AND name = :settingName")
    suspend fun updateSettingEnabled(sensorId: String, settingName: String, enabled: Boolean)

    @Query("UPDATE sensor_settings SET value = :value WHERE sensor_id = :sensorId AND name = :settingName")
    suspend fun updateSettingValue(sensorId: String, settingName: String, value: String)

    @Query(
        "UPDATE sensors SET last_sent_state = :state, last_sent_icon = :icon WHERE id = :sensorId AND server_id = :serverId",
    )
    suspend fun updateLastSentStateAndIcon(sensorId: String, serverId: Int, state: String?, icon: String?)

    @Query("UPDATE sensors SET last_sent_state = :state, last_sent_icon = :icon WHERE id = :sensorId")
    suspend fun updateLastSentStatesAndIcons(sensorId: String, state: String?, icon: String?)

    @Transaction
    suspend fun setSensorEnabled(sensorId: String, serverIds: List<Int>, enabled: Boolean) {
        serverIds.forEach {
            setSensorsEnabled(listOf(sensorId), it, enabled)
        }
    }

    @Transaction
    suspend fun setSensorsEnabled(sensorIds: List<String>, serverId: Int, enabled: Boolean) {
        coroutineScope {
            sensorIds.map { sensorId ->
                async {
                    // Keep an existing row's other fields, otherwise start from a fresh entity; upsert
                    // creates it when absent and overwrites it when present.
                    val sensor = get(sensorId, serverId)
                        ?.copy(enabled = enabled, lastSentState = null, lastSentIcon = null)
                        ?: Sensor(sensorId, serverId, enabled, state = "")
                    upsert(sensor)
                }
            }.awaitAll()
        }
    }
}
