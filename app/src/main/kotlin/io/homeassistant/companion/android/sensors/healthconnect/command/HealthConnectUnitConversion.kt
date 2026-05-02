package io.homeassistant.companion.android.sensors.healthconnect.command

import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Volume
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType

/**
 * Converts a `command_health_connect_write` payload's `(value, unit)` pair into the
 * canonical-unit double the [io.homeassistant.companion.android.sensors.healthconnect.HealthConnectWriteRepository]
 * expects per data type.
 *
 * The conversion math itself is delegated to Health Connect's own unit classes
 * (`Mass.pounds(...).inKilograms`, `Temperature.fahrenheit(...).inCelsius`, etc.) — that
 * way we don't keep a private copy of conversion constants that could drift from the
 * SDK's source of truth, and we automatically pick up any precision tweaks HC ships.
 *
 * If `unit` is null or blank the value is returned unchanged (caller's responsibility
 * to send the canonical unit). Unknown unit strings raise
 * [HealthConnectWriteCommandPayload.InvalidPayloadException] so a typo in an automation
 * surfaces as a clear notification rather than a silently-wrong record in HC.
 */
internal object HealthConnectUnitConversion {

    fun toCanonical(dataType: HealthConnectDataType, value: Double, unit: String?): Double {
        if (unit.isNullOrBlank()) return value
        val key = unit.trim().lowercase().replace("/", "_per_").replace(" ", "")
        val converter = converters[dataType]
            ?: throw HealthConnectWriteCommandPayload.InvalidPayloadException(
                "Data type ${dataType.key} does not accept a unit field",
            )
        return converter[key]?.invoke(value)
            ?: throw HealthConnectWriteCommandPayload.InvalidPayloadException(
                "Unknown unit '$unit' for ${dataType.key}. Accepted: ${converter.keys.sorted()}",
            )
    }

    private val mass: Map<String, (Double) -> Double> = mapOf(
        "kg" to { it },
        "kilograms" to { it },
        "kilogram" to { it },
        "g" to { Mass.grams(it).inKilograms },
        "grams" to { Mass.grams(it).inKilograms },
        "gram" to { Mass.grams(it).inKilograms },
        "mg" to { Mass.milligrams(it).inKilograms },
        "milligrams" to { Mass.milligrams(it).inKilograms },
        "lb" to { Mass.pounds(it).inKilograms },
        "lbs" to { Mass.pounds(it).inKilograms },
        "pound" to { Mass.pounds(it).inKilograms },
        "pounds" to { Mass.pounds(it).inKilograms },
        "oz" to { Mass.ounces(it).inKilograms },
        "ounce" to { Mass.ounces(it).inKilograms },
        "ounces" to { Mass.ounces(it).inKilograms },
    )

    private val length: Map<String, (Double) -> Double> = mapOf(
        "m" to { it },
        "meter" to { it },
        "meters" to { it },
        "metres" to { it },
        "km" to { Length.kilometers(it).inMeters },
        "kilometer" to { Length.kilometers(it).inMeters },
        "kilometers" to { Length.kilometers(it).inMeters },
        "cm" to { it / 100.0 },
        "centimeter" to { it / 100.0 },
        "centimeters" to { it / 100.0 },
        "centimetre" to { it / 100.0 },
        "centimetres" to { it / 100.0 },
        "mi" to { Length.miles(it).inMeters },
        "mile" to { Length.miles(it).inMeters },
        "miles" to { Length.miles(it).inMeters },
        "ft" to { Length.feet(it).inMeters },
        "foot" to { Length.feet(it).inMeters },
        "feet" to { Length.feet(it).inMeters },
        "in" to { Length.inches(it).inMeters },
        "inch" to { Length.inches(it).inMeters },
        "inches" to { Length.inches(it).inMeters },
    )

    private val temperatureCelsius: Map<String, (Double) -> Double> = mapOf(
        "c" to { it },
        "°c" to { it },
        "celsius" to { it },
        "f" to { Temperature.fahrenheit(it).inCelsius },
        "°f" to { Temperature.fahrenheit(it).inCelsius },
        "fahrenheit" to { Temperature.fahrenheit(it).inCelsius },
    )

    private val bloodGlucose: Map<String, (Double) -> Double> = mapOf(
        "mmol_per_l" to { it },
        "mmol/l" to { it },
        "mmoll" to { it },
        "mg_per_dl" to { BloodGlucose.milligramsPerDeciliter(it).inMillimolesPerLiter },
        "mg/dl" to { BloodGlucose.milligramsPerDeciliter(it).inMillimolesPerLiter },
        "mgdl" to { BloodGlucose.milligramsPerDeciliter(it).inMillimolesPerLiter },
    )

    private val volume: Map<String, (Double) -> Double> = mapOf(
        "l" to { it },
        "liter" to { it },
        "liters" to { it },
        "litre" to { it },
        "litres" to { it },
        "ml" to { Volume.milliliters(it).inLiters },
        "milliliter" to { Volume.milliliters(it).inLiters },
        "milliliters" to { Volume.milliliters(it).inLiters },
        "fl_oz" to { Volume.fluidOuncesUs(it).inLiters },
        "floz" to { Volume.fluidOuncesUs(it).inLiters },
        "fl_oz_us" to { Volume.fluidOuncesUs(it).inLiters },
    )

    private val energy: Map<String, (Double) -> Double> = mapOf(
        "kcal" to { it },
        "kilocalorie" to { it },
        "kilocalories" to { it },
        "cal" to { Energy.calories(it).inKilocalories },
        "calorie" to { Energy.calories(it).inKilocalories },
        "calories" to { Energy.calories(it).inKilocalories },
        "j" to { Energy.joules(it).inKilocalories },
        "joule" to { Energy.joules(it).inKilocalories },
        "joules" to { Energy.joules(it).inKilocalories },
        "kj" to { Energy.kilojoules(it).inKilocalories },
        "kilojoule" to { Energy.kilojoules(it).inKilocalories },
        "kilojoules" to { Energy.kilojoules(it).inKilocalories },
    )

    private val power: Map<String, (Double) -> Double> = mapOf(
        "kcal_per_day" to { it },
        "kilocalories_per_day" to { it },
        "w" to { Power.watts(it).inKilocaloriesPerDay },
        "watts" to { Power.watts(it).inKilocaloriesPerDay },
        "watt" to { Power.watts(it).inKilocaloriesPerDay },
    )

    /** Percentages are stored as plain doubles; HC's `Percentage(...)` expects 0..100. */
    private val percentage: Map<String, (Double) -> Double> = mapOf(
        "%" to { it },
        "percent" to { it },
        "percentage" to { it },
        "fraction" to { it * 100.0 },
        "ratio" to { it * 100.0 },
    )

    /** HRV is a plain Double in HC; only ms is meaningful. */
    private val milliseconds: Map<String, (Double) -> Double> = mapOf(
        "ms" to { it },
        "millisecond" to { it },
        "milliseconds" to { it },
        "s" to { it * 1000.0 },
        "second" to { it * 1000.0 },
        "seconds" to { it * 1000.0 },
    )

    private val converters: Map<HealthConnectDataType, Map<String, (Double) -> Double>> = mapOf(
        HealthConnectDataType.Weight to mass,
        HealthConnectDataType.BodyWaterMass to mass,
        HealthConnectDataType.BoneMass to mass,
        HealthConnectDataType.LeanBodyMass to mass,
        HealthConnectDataType.Height to length,
        HealthConnectDataType.Distance to length,
        HealthConnectDataType.ElevationGained to length,
        HealthConnectDataType.BodyTemperature to temperatureCelsius,
        HealthConnectDataType.BasalBodyTemperature to temperatureCelsius,
        HealthConnectDataType.BloodGlucose to bloodGlucose,
        HealthConnectDataType.Hydration to volume,
        HealthConnectDataType.ActiveCaloriesBurned to energy,
        HealthConnectDataType.TotalCaloriesBurned to energy,
        HealthConnectDataType.BasalMetabolicRate to power,
        HealthConnectDataType.BodyFat to percentage,
        HealthConnectDataType.OxygenSaturation to percentage,
        HealthConnectDataType.HeartRateVariability to milliseconds,
    )
}
