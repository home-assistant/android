package io.homeassistant.companion.android.database.sensor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SensorDao {

    @Query("SELECT * FROM Sensors WHERE id = :id")
    fun get(id: String): Sensor?

    @Transaction
    @Query("SELECT * FROM Sensors WHERE id = :id")
    fun getFull(id: String): SensorWithAttributes?

    @Transaction
    @Query("SELECT * FROM sensor_settings WHERE sensor_id = :id")
    fun getSettings(id: String): List<Setting>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun add(sensor: Sensor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(attribute: Attribute)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(setting: Setting)

    @Query("DELETE FROM sensor_settings WHERE sensor_id = :sensorId AND name = :settingName")
    fun removeSetting(sensorId: String, settingName: String)

    @Update
    fun update(sensor: Sensor)

    @Query("DELETE FROM sensor_attributes WHERE sensor_id = :sensorId")
    fun clearAttributes(sensorId: String)

    @Query("UPDATE sensors SET last_sent_state = :state WHERE id = :sensorId")
    fun updateLastSendState(sensorId: String, state: String)

    @Query("SELECT COUNT(id) FROM sensors WHERE enabled = 1")
    fun getEnabledCount(): Int?
}
