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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun add(sensor: Sensor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(attribute: Attribute)

    @Update
    fun update(sensor: Sensor)

    @Query("DELETE FROM sensor_attributes WHERE sensor_id = :sensorId")
    fun clearAttributes(sensorId: String)
}
