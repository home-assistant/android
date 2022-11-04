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

    @Query("SELECT * FROM Sensors WHERE id = :id")
    fun get(id: String): Sensor?

    @Query("SELECT * FROM sensors")
    fun getAllFlow(): Flow<List<Sensor>>

    @Transaction
    @Query("SELECT * FROM Sensors WHERE id = :id")
    fun getFull(id: String): SensorWithAttributes?

    @Transaction
    @Query("SELECT * FROM Sensors WHERE id = :id")
    fun getFullFlow(id: String): Flow<SensorWithAttributes?>

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

    @Query("UPDATE sensors SET last_sent_state = :state, last_sent_icon = :icon WHERE id = :sensorId")
    suspend fun updateLastSentStateAndIcon(sensorId: String, state: String?, icon: String?)

    @Query("SELECT COUNT(id) FROM sensors WHERE enabled = 1")
    suspend fun getEnabledCount(): Int?

    @Transaction
    suspend fun setSensorsEnabled(sensorIds: List<String>, enabled: Boolean) {
        coroutineScope {
            sensorIds.map { sensorId ->
                async {
                    val sensorEntity = get(sensorId)
                    if (sensorEntity != null) {
                        update(sensorEntity.copy(enabled = enabled, lastSentState = null, lastSentIcon = null))
                    } else {
                        add(Sensor(sensorId, enabled, state = ""))
                    }
                }
            }.awaitAll()
        }
    }

    @Transaction
    fun getOrDefault(sensorId: String, permission: Boolean, enabledByDefault: Boolean): Sensor {
        var sensor = get(sensorId)

        if (sensor == null) {
            // If we haven't created the entity yet do so and default to enabled if required
            sensor = Sensor(sensorId, enabled = permission && enabledByDefault, state = "")
            add(sensor)
        } else if (sensor.enabled && !permission) {
            // If we don't have permission but we are still enabled then we aren't really enabled.
            sensor.enabled = false
            update(sensor)
        }

        return sensor
    }
}
