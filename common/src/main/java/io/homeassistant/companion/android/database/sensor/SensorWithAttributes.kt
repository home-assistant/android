package io.homeassistant.companion.android.database.sensor

import androidx.room.Embedded
import androidx.room.Relation
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.sensors.SensorManager

data class SensorWithAttributes(
    @Embedded
    val sensor: Sensor,
    @Relation(
        parentColumn = "id",
        entityColumn = "sensor_id"
    )
    val attributes: List<Attribute>
) {
    fun toSensorRegistration(basicSensor: SensorManager.BasicSensor): SensorRegistration<Any> {
        var objectMapper: ObjectMapper? = null
        val attributes = attributes.associate {
            val attributeValue = when (it.valueType) {
                "listboolean", "listfloat", "listlong", "listint", "liststring" -> {
                    if (objectMapper == null) objectMapper = jacksonObjectMapper()
                    objectMapper?.let { mapper ->
                        when (it.valueType) {
                            "listboolean" -> mapper.readValue<List<Boolean>>(it.value)
                            "listfloat" -> mapper.readValue<List<Number>>(it.value)
                            "listlong" -> mapper.readValue<List<Long>>(it.value)
                            "listint" -> mapper.readValue<List<Int>>(it.value)
                            else -> mapper.readValue<List<String>>(it.value)
                        }
                    } ?: it.value // Fallback: provide JSON string, but shouldn't happen
                }
                "boolean" -> it.value.toBoolean()
                "float" -> it.value.toFloat()
                "long" -> it.value.toLong()
                "int" -> it.value.toInt()
                "string" -> it.value
                else -> throw IllegalArgumentException("Attribute: ${it.name} is of unknown type: ${it.valueType}")
            }
            it.name to attributeValue
        }
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
            sensor.type.ifBlank { basicSensor.type },
            sensor.icon.ifBlank { basicSensor.statelessIcon },
            attributes,
            sensor.name,
            sensor.deviceClass?.ifBlank { null } ?: basicSensor.deviceClass,
            sensor.unitOfMeasurement?.ifBlank { null } ?: basicSensor.unitOfMeasurement,
            sensor.stateClass?.ifBlank { null } ?: basicSensor.stateClass,
            sensor.entityCategory?.ifBlank { null } ?: basicSensor.entityCategory,
            !sensor.enabled
        )
    }
}
