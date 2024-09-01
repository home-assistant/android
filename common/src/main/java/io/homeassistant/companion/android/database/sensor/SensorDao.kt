package io.homeassistant.companion.android.database.sensor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {

    @Query("SELECT * FROM sensors WHERE id = :id")
    fun get(id: String): List<Sensor>

    @Query("SELECT * FROM sensors WHERE id = :id AND server_id = :serverId")
    fun get(id: String, serverId: Int): Sensor?

    @Query("SELECT * FROM sensors")
    fun getAllFlow(): Flow<List<Sensor>>

    @Transaction
    @Query("SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id")
    fun getFull(id: String): Map<Sensor, List<Attribute>>

    @Transaction
    @Query("SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id AND sensors.server_id = :serverId")
    fun getFull(id: String, serverId: Int): Map<Sensor, List<Attribute>>

    @Transaction
    @Query("SELECT * FROM sensors LEFT JOIN sensor_attributes ON sensors.id = sensor_attributes.sensor_id WHERE sensors.id = :id")
    fun getFullFlow(id: String): Flow<Map<Sensor, List<Attribute>>>

    @Query("SELECT * FROM sensors WHERE server_id = :serverId")
    suspend fun getAllServer(serverId: Int): List<Sensor>

    @Query("SELECT * FROM sensors WHERE NOT(server_id IN (:serverIds))")
    suspend fun getAllExceptServer(serverIds: List<Int>): List<Sensor>

    @Transaction
    @Query("SELECT * FROM sensor_settings WHERE sensor_id = :id")
    fun getSettings(id: String): List<SensorSetting>

    @Transaction
    @Query("SELECT * FROM sensor_settings WHERE sensor_id = :id ORDER BY sensor_id")
    fun getSettingsFlow(id: String): Flow<List<SensorSetting>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun add(sensor: Sensor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(attribute: Attribute)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(attributes: List<Attribute>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(sensorSetting: SensorSetting)

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
    fun removeSetting(sensorId: String, settingName: String)

    @Query("DELETE FROM sensor_settings WHERE sensor_id = :sensorId AND name IN (:settingNames)")
    fun removeSettings(sensorId: String, settingNames: List<String>)

    @Update
    fun update(sensor: Sensor)

    @Query("DELETE FROM sensor_attributes WHERE sensor_id = :sensorId")
    fun clearAttributes(sensorId: String)

    @Transaction
    fun replaceAllAttributes(sensorId: String, attributes: List<Attribute>) {
        clearAttributes(sensorId)
        add(attributes)
    }

    @Query("UPDATE sensor_settings SET enabled = :enabled WHERE sensor_id = :sensorId AND name = :settingName")
    suspend fun updateSettingEnabled(sensorId: String, settingName: String, enabled: Boolean)

    @Query("UPDATE sensor_settings SET value = :value WHERE sensor_id = :sensorId AND name = :settingName")
    fun updateSettingValue(sensorId: String, settingName: String, value: String)

    @Query("UPDATE sensors SET last_sent_state = :state, last_sent_icon = :icon WHERE id = :sensorId AND server_id = :serverId")
    suspend fun updateLastSentStateAndIcon(sensorId: String, serverId: Int, state: String?, icon: String?)

    @Query("UPDATE sensors SET last_sent_state = :state, last_sent_icon = :icon WHERE id = :sensorId")
    suspend fun updateLastSentStatesAndIcons(sensorId: String, state: String?, icon: String?)

    @Query("SELECT COUNT(id) FROM sensors WHERE enabled = 1")
    suspend fun getEnabledCount(): Int?

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
                    val sensorEntity = get(sensorId, serverId)
                    if (sensorEntity != null) {
                        update(sensorEntity.copy(enabled = enabled, lastSentState = null, lastSentIcon = null))
                    } else {
                        add(Sensor(sensorId, serverId, enabled, state = ""))
                    }
                }
            }.awaitAll()
        }
    }

    @Transaction
    fun getOrDefault(sensorId: String, serverId: Int, permission: Boolean, enabledByDefault: Boolean): Sensor? {
        val sensor = get(sensorId, serverId)

        if (sensor?.enabled == true && !permission) {
            // If we don't have permission but we are still enabled then we aren't really enabled.
            sensor.enabled = false
            update(sensor)
        }

        return sensor
    }

    @Transaction
    fun getAnyIsEnabled(sensorId: String, servers: List<Int>, permission: Boolean, enabledByDefault: Boolean): Boolean {
        // Create and update entries for all
        var sensorList = get(sensorId)
        var changedList = false
        if (sensorList.isEmpty()) {
            // If we haven't created the entity yet do so and default to enabled if required
            servers.forEach {
                add(Sensor(sensorId, it, enabled = permission && enabledByDefault, state = ""))
            }
            changedList = true
        } else {
            if (!permission) {
                // If we don't have permission but we are still enabled then we aren't really enabled.
                sensorList.filter { it.enabled }.forEach {
                    update(it.apply { enabled = false })
                    changedList = true
                }
            }
            val newServers = servers.filter { it !in sensorList.map { sensor -> sensor.serverId } }
            if (newServers.isNotEmpty()) {
                // If we have any new servers but don't have entries create one for updates.
                val singleSensor = sensorList.maxBy { it.enabled } // Prefer enabled
                newServers.forEach {
                    add(
                        singleSensor.copy(
                            serverId = it,
                            registered = null,
                            state = "",
                            stateType = "",
                            lastSentState = null,
                            lastSentIcon = null,
                            coreRegistration = null
                        )
                    )
                }
                changedList = true
            }
        }
        if (changedList) sensorList = get(sensorId)

        // Return if any are enabled
        return if (sensorList.isEmpty()) {
            false // No servers
        } else {
            sensorList.any { it.enabled && permission }
        }
    }
}
