package io.homeassistant.companion.android.database.sensor

import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.kotlinJsonMapper

data class SensorWithAttributes(val sensor: Sensor, val attributes: List<Attribute>) {
    fun toSensorRegistration(basicSensor: SensorManager.BasicSensor): SensorRegistration<Any> {
        val attributes = attributes.associate {
            val attributeValue = when (it.valueType) {
                "listboolean", "listfloat", "listlong", "listint", "liststring" -> {
                    when (it.valueType) {
                        "listboolean" -> kotlinJsonMapper.decodeFromString<List<Boolean>>(it.value)
                        "listfloat" -> kotlinJsonMapper.decodeFromString<List<Float>>(it.value)
                        "listlong" -> kotlinJsonMapper.decodeFromString<List<Long>>(it.value)
                        "listint" -> kotlinJsonMapper.decodeFromString<List<Int>>(it.value)
                        else -> kotlinJsonMapper.decodeFromString<List<String>>(it.value)
                    }
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
            sensor.serverId,
            state,
            sensor.type.ifBlank { basicSensor.type },
            sensor.icon.ifBlank { basicSensor.statelessIcon },
            attributes,
            sensor.name,
            sensor.deviceClass?.ifBlank { null } ?: basicSensor.deviceClass,
            sensor.unitOfMeasurement?.ifBlank { null } ?: basicSensor.unitOfMeasurement,
            sensor.stateClass?.ifBlank { null } ?: basicSensor.stateClass,
            sensor.entityCategory?.ifBlank { null } ?: basicSensor.entityCategory,
            !sensor.enabled,
        )
    }
}

fun Map<Sensor, List<Attribute>>.toSensorWithAttributes(): SensorWithAttributes? =
    entries.map { SensorWithAttributes(it.key, it.value) }.firstOrNull()

fun Map<Sensor, List<Attribute>>.toSensorsWithAttributes(): List<SensorWithAttributes> =
    entries.map { SensorWithAttributes(it.key, it.value) }
