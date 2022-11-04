package io.homeassistant.companion.android.database.sensor

import androidx.room.Embedded
import androidx.room.Relation

data class SensorWithSettings(
    @Embedded
    val sensor: Sensor,
    @Relation(
        parentColumn = "id",
        entityColumn = "sensor_id"
    )
    val sensorSettings: List<SensorSetting>
)
