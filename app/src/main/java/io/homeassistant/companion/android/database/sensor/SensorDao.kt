package io.homeassistant.companion.android.database.sensor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SensorDao {

    @Query("SELECT * FROM Sensors WHERE unique_id = :uniqueId")
    fun get(uniqueId: String): Sensor?

    @Insert
    fun add(sensor: Sensor)

    @Update
    fun update(sensor: Sensor)
}
