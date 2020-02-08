package io.homeassistant.companion.android.domain.integration

data class SensorRegistration<T>(
    val uniqueId: String,
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>,
    val name: String,
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null

) {
    constructor(
        sensor: Sensor<T>,
        name: String,
        deviceClass: String? = null,
        unitOfMeasurement: String? = null
    ) : this(
        sensor.uniqueId,
        sensor.state,
        sensor.type,
        sensor.icon,
        sensor.attributes,
        name,
        deviceClass,
        unitOfMeasurement

    )
}
