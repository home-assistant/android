package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import kotlin.reflect.KClass

/**
 * Strongly-typed catalogue of every Health Connect data type the companion app knows about.
 *
 * Each entry binds together:
 *  - [key]: the stable identifier used in the FCM `command_health_connect_write` payload's
 *    `data_type` field. Server-side automations write this value, so it must remain stable
 *    across releases.
 *  - [recordClass]: the [androidx.health.connect.client.records.Record] subclass produced
 *    when writing, and queried when reading via the Changes API.
 *  - [sensorIds]: the existing `HealthConnectSensorManager` sensor IDs that surface this
 *    data type to Home Assistant. Most data types have a 1:1 mapping; blood pressure is
 *    1:N because it produces both a systolic and a diastolic sensor from the same record.
 *
 * The Health Connect read/write permission strings are derived from [recordClass] via
 * [HealthPermission.getReadPermission] / [HealthPermission.getWritePermission] — there is
 * no need to hard-code them.
 */
sealed class HealthConnectDataType(
    val key: String,
    val recordClass: KClass<out Record>,
    val sensorIds: List<String>,
) {
    /** Permission string required to read this data type from Health Connect. */
    val readPermission: String get() = HealthPermission.getReadPermission(recordClass)

    /** Permission string required to write this data type to Health Connect. */
    val writePermission: String get() = HealthPermission.getWritePermission(recordClass)

    object ActiveCaloriesBurned : HealthConnectDataType(
        key = "active_calories_burned",
        recordClass = ActiveCaloriesBurnedRecord::class,
        sensorIds = listOf("health_connect_active_calories_burned"),
    )

    object BasalBodyTemperature : HealthConnectDataType(
        key = "basal_body_temperature",
        recordClass = BasalBodyTemperatureRecord::class,
        sensorIds = listOf("health_connect_basal_body_temperature"),
    )

    object BasalMetabolicRate : HealthConnectDataType(
        key = "basal_metabolic_rate",
        recordClass = BasalMetabolicRateRecord::class,
        sensorIds = listOf("health_connect_basal_metabolic_rate"),
    )

    object BloodGlucose : HealthConnectDataType(
        key = "blood_glucose",
        recordClass = BloodGlucoseRecord::class,
        sensorIds = listOf("health_connect_blood_glucose"),
    )

    object BloodPressure : HealthConnectDataType(
        key = "blood_pressure",
        recordClass = BloodPressureRecord::class,
        sensorIds = listOf(
            "health_connect_systolic_blood_pressure",
            "health_connect_diastolic_blood_pressure",
        ),
    )

    object BodyFat : HealthConnectDataType(
        key = "body_fat",
        recordClass = BodyFatRecord::class,
        sensorIds = listOf("health_connect_body_fat"),
    )

    object BodyTemperature : HealthConnectDataType(
        key = "body_temperature",
        recordClass = BodyTemperatureRecord::class,
        sensorIds = listOf("health_connect_body_temperature"),
    )

    object BodyWaterMass : HealthConnectDataType(
        key = "body_water_mass",
        recordClass = BodyWaterMassRecord::class,
        sensorIds = listOf("health_connect_body_water_mass"),
    )

    object BoneMass : HealthConnectDataType(
        key = "bone_mass",
        recordClass = BoneMassRecord::class,
        sensorIds = listOf("health_connect_bone_mass"),
    )

    object Distance : HealthConnectDataType(
        key = "distance",
        recordClass = DistanceRecord::class,
        sensorIds = listOf("health_connect_distance"),
    )

    object ElevationGained : HealthConnectDataType(
        key = "elevation_gained",
        recordClass = ElevationGainedRecord::class,
        sensorIds = listOf("health_connect_elevation_gained"),
    )

    object FloorsClimbed : HealthConnectDataType(
        key = "floors_climbed",
        recordClass = FloorsClimbedRecord::class,
        sensorIds = listOf("health_connect_floors_climbed"),
    )

    object HeartRate : HealthConnectDataType(
        key = "heart_rate",
        recordClass = HeartRateRecord::class,
        sensorIds = listOf("health_connect_heart_rate"),
    )

    object HeartRateVariability : HealthConnectDataType(
        key = "heart_rate_variability",
        recordClass = HeartRateVariabilityRmssdRecord::class,
        sensorIds = listOf("health_connect_heart_rate_variability"),
    )

    object Height : HealthConnectDataType(
        key = "height",
        recordClass = HeightRecord::class,
        sensorIds = listOf("health_connect_height"),
    )

    object Hydration : HealthConnectDataType(
        key = "hydration",
        recordClass = HydrationRecord::class,
        sensorIds = listOf("health_connect_hydration"),
    )

    object LeanBodyMass : HealthConnectDataType(
        key = "lean_body_mass",
        recordClass = LeanBodyMassRecord::class,
        sensorIds = listOf("health_connect_lean_body_mass"),
    )

    object OxygenSaturation : HealthConnectDataType(
        key = "oxygen_saturation",
        recordClass = OxygenSaturationRecord::class,
        sensorIds = listOf("health_connect_oxygen_saturation"),
    )

    object RespiratoryRate : HealthConnectDataType(
        key = "respiratory_rate",
        recordClass = RespiratoryRateRecord::class,
        sensorIds = listOf("health_connect_respiratory_rate"),
    )

    object RestingHeartRate : HealthConnectDataType(
        key = "resting_heart_rate",
        recordClass = RestingHeartRateRecord::class,
        sensorIds = listOf("health_connect_resting_heart_rate"),
    )

    object Sleep : HealthConnectDataType(
        key = "sleep",
        recordClass = SleepSessionRecord::class,
        sensorIds = listOf("health_connect_sleep_duration"),
    )

    object Steps : HealthConnectDataType(
        key = "steps",
        recordClass = StepsRecord::class,
        sensorIds = listOf("health_connect_steps"),
    )

    object TotalCaloriesBurned : HealthConnectDataType(
        key = "total_calories_burned",
        recordClass = TotalCaloriesBurnedRecord::class,
        sensorIds = listOf("health_connect_total_calories_burned"),
    )

    object Vo2Max : HealthConnectDataType(
        key = "vo2_max",
        recordClass = Vo2MaxRecord::class,
        sensorIds = listOf("health_connect_vo2_max"),
    )

    object Weight : HealthConnectDataType(
        key = "weight",
        recordClass = WeightRecord::class,
        sensorIds = listOf("health_connect_weight"),
    )

    companion object {
        /** All known data types. Order is not significant. */
        val all: List<HealthConnectDataType> = listOf(
            ActiveCaloriesBurned,
            BasalBodyTemperature,
            BasalMetabolicRate,
            BloodGlucose,
            BloodPressure,
            BodyFat,
            BodyTemperature,
            BodyWaterMass,
            BoneMass,
            Distance,
            ElevationGained,
            FloorsClimbed,
            HeartRate,
            HeartRateVariability,
            Height,
            Hydration,
            LeanBodyMass,
            OxygenSaturation,
            RespiratoryRate,
            RestingHeartRate,
            Sleep,
            Steps,
            TotalCaloriesBurned,
            Vo2Max,
            Weight,
        )

        /**
         * Resolve a data type by its FCM payload key, or `null` when the key is unknown.
         * Used by [HealthConnectWriteCommandHandler] when parsing incoming commands.
         */
        fun fromKey(key: String): HealthConnectDataType? = all.firstOrNull { it.key == key }

        /**
         * Resolve a data type by one of its sensor IDs, or `null` when no sensor maps to
         * this ID. A sensor ID always maps to at most one data type.
         */
        fun fromSensorId(sensorId: String): HealthConnectDataType? =
            all.firstOrNull { sensorId in it.sensorIds }
    }
}
