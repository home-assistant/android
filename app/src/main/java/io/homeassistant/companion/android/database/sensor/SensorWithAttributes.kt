package io.homeassistant.companion.android.database.sensor

import androidx.room.Embedded
import androidx.room.Relation
import io.homeassistant.companion.android.domain.integration.SensorRegistration

data class SensorWithAttributes(
    @Embedded
    val sensor: Sensor,
    @Relation(
        parentColumn = "id",
        entityColumn = "sensor_id"
    )
    val attributes: List<Attribute>
) {
    fun toSensorRegistration(): SensorRegistration<Any> {
        val attributes = attributes.map { it.name to it.value }.toMap()
        val state = when (sensor.stateType) {
            "" -> ""
            "boolean" -> sensor.state.toBoolean()
            "float" -> sensor.state.toFloat()
            "int" -> sensor.state.toInt()
            "string" -> sensor.state
            else -> throw IllegalArgumentException("State is of unknown type: ${sensor.stateType}")
        }
        return SensorRegistration(
            sensor.id,
            state,
            sensor.type,
            sensor.icon,
            attributes,
            sensor.name,
            sensor.deviceClass,
            sensor.unitOfMeasurement
        )
    }
}
