package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.VisibleForTesting
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
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
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import timber.log.Timber

@Singleton
class HealthConnectSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        fun getPermissionIntent(): Intent? = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

        fun getPermissionResultContract(): ActivityResultContract<Set<String>, Set<String>>? =
            PermissionController.createRequestPermissionResultContract()

        @ProvidesSensor
        val activeCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_active_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_active_calories_burned,
            commonR.string.sensor_description_active_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val basalBodyTemperature = SensorManager.BasicSensor(
            id = "health_connect_basal_body_temperature",
            type = "sensor",
            commonR.string.basic_sensor_name_basal_body_temperature,
            commonR.string.sensor_description_basal_body_temperature,
            "mdi:thermometer",
            deviceClass = "temperature",
            unitOfMeasurement = "°C",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val basalMetabolicRate = SensorManager.BasicSensor(
            id = "health_connect_basal_metabolic_rate",
            type = "sensor",
            commonR.string.basic_sensor_name_basal_metabolic_rate,
            commonR.string.sensor_description_basal_metabolic_rate,
            "mdi:fire",
            unitOfMeasurement = "kcal/day",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val bloodGlucose = SensorManager.BasicSensor(
            id = "health_connect_blood_glucose",
            type = "sensor",
            commonR.string.basic_sensor_name_blood_glucose,
            commonR.string.sensor_description_blood_glucose,
            "mdi:diabetes",
            deviceClass = "blood_glucose_concentration",
            unitOfMeasurement = "mg/dL",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val bodyFat = SensorManager.BasicSensor(
            id = "health_connect_body_fat",
            type = "sensor",
            commonR.string.basic_sensor_name_body_fat,
            commonR.string.sensor_description_body_fat,
            "mdi:scale-bathroom",
            unitOfMeasurement = "%",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val bodyWaterMass = SensorManager.BasicSensor(
            id = "health_connect_body_water_mass",
            type = "sensor",
            commonR.string.basic_sensor_name_body_water_mass,
            commonR.string.sensor_description_body_water_mass,
            "mdi:water",
            deviceClass = "weight",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val bodyTemperature = SensorManager.BasicSensor(
            id = "health_connect_body_temperature",
            type = "sensor",
            commonR.string.basic_sensor_name_body_temperature,
            commonR.string.sensor_description_body_temperature,
            "mdi:thermometer",
            deviceClass = "temperature",
            unitOfMeasurement = "°C",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val boneMass = SensorManager.BasicSensor(
            id = "health_connect_bone_mass",
            type = "sensor",
            commonR.string.basic_sensor_name_bone_mass,
            commonR.string.sensor_description_bone_mass,
            "mdi:bone",
            deviceClass = "weight",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val diastolicBloodPressure = SensorManager.BasicSensor(
            id = "health_connect_diastolic_blood_pressure",
            type = "sensor",
            commonR.string.basic_sensor_name_diastolic_blood_pressure,
            commonR.string.sensor_description_diastolic_blood_pressure,
            "mdi:heart-pulse",
            deviceClass = "pressure",
            unitOfMeasurement = "mmHg",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val distance = SensorManager.BasicSensor(
            id = "health_connect_distance",
            type = "sensor",
            commonR.string.sensor_name_daily_distance,
            commonR.string.sensor_description_distance,
            "mdi:map-marker-distance",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val elevationGained = SensorManager.BasicSensor(
            id = "health_connect_elevation_gained",
            type = "sensor",
            commonR.string.basic_sensor_name_daily_elevation_gained,
            commonR.string.sensor_description_elevation_gained,
            "mdi:elevation-rise",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val floorsClimbed = SensorManager.BasicSensor(
            id = "health_connect_floors_climbed",
            type = "sensor",
            commonR.string.sensor_name_daily_floors,
            commonR.string.sensor_description_floors_climbed,
            "mdi:stairs",
            unitOfMeasurement = "floors",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val heartRate = SensorManager.BasicSensor(
            id = "health_connect_heart_rate",
            type = "sensor",
            commonR.string.sensor_name_heart_rate,
            commonR.string.sensor_description_health_connect_heart_rate,
            "mdi:heart-pulse",
            unitOfMeasurement = "bpm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val heartRateVariability = SensorManager.BasicSensor(
            id = "health_connect_heart_rate_variability",
            type = "sensor",
            commonR.string.basic_sensor_name_heart_rate_variability,
            commonR.string.sensor_description_heart_rate_variability,
            "mdi:heart-pulse",
            deviceClass = "duration",
            unitOfMeasurement = "ms",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val height = SensorManager.BasicSensor(
            id = "health_connect_height",
            type = "sensor",
            commonR.string.basic_sensor_name_height,
            commonR.string.sensor_description_height,
            "mdi:human-male-height",
            deviceClass = "distance",
            unitOfMeasurement = "m",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val hydration = SensorManager.BasicSensor(
            id = "health_connect_hydration",
            type = "sensor",
            commonR.string.basic_sensor_name_hydration,
            commonR.string.sensor_description_hydration,
            "mdi:cup-water",
            unitOfMeasurement = "mL",
            deviceClass = "volume",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val leanBodyMass = SensorManager.BasicSensor(
            id = "health_connect_lean_body_mass",
            type = "sensor",
            commonR.string.basic_sensor_name_lean_body_mass,
            commonR.string.sensor_description_lean_body_mass,
            "mdi:scale-bathroom",
            deviceClass = "weight",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val oxygenSaturation = SensorManager.BasicSensor(
            id = "health_connect_oxygen_saturation",
            type = "sensor",
            commonR.string.basic_sensor_name_oxygen_saturation,
            commonR.string.sensor_description_oxygen_saturation,
            "mdi:sleep",
            unitOfMeasurement = "%",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val respiratoryRate = SensorManager.BasicSensor(
            id = "health_connect_respiratory_rate",
            type = "sensor",
            commonR.string.basic_sensor_name_respiratory_rate,
            commonR.string.sensor_description_respiratory_rate,
            "mdi:account-voice",
            unitOfMeasurement = "bpm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val restingHeartRate = SensorManager.BasicSensor(
            id = "health_connect_resting_heart_rate",
            type = "sensor",
            commonR.string.basic_sensor_name_resting_heart_rate,
            commonR.string.sensor_description_resting_heart_rate,
            "mdi:heart-pulse",
            unitOfMeasurement = "bpm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val sleepDuration = SensorManager.BasicSensor(
            id = "health_connect_sleep_duration",
            type = "sensor",
            commonR.string.basic_sensor_name_sleep_duration,
            commonR.string.sensor_description_sleep_duration,
            "mdi:sleep",
            deviceClass = "duration",
            unitOfMeasurement = "min",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val steps = SensorManager.BasicSensor(
            id = "health_connect_steps",
            type = "sensor",
            commonR.string.sensor_name_daily_steps,
            commonR.string.sensor_description_steps,
            "mdi:walk",
            unitOfMeasurement = "steps",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val systolicBloodPressure = SensorManager.BasicSensor(
            id = "health_connect_systolic_blood_pressure",
            type = "sensor",
            commonR.string.basic_sensor_name_systolic_blood_pressure,
            commonR.string.sensor_description_systolic_blood_pressure,
            "mdi:heart-pulse",
            deviceClass = "pressure",
            unitOfMeasurement = "mmHg",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val totalCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_total_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_total_calories_burned,
            commonR.string.sensor_description_total_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val vo2Max = SensorManager.BasicSensor(
            id = "health_connect_vo2_max",
            type = "sensor",
            commonR.string.basic_sensor_name_vo2_max,
            commonR.string.sensor_description_vo2_max,
            "mdi:heart",
            unitOfMeasurement = "mL/kg/min",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val weight = SensorManager.BasicSensor(
            id = "health_connect_weight",
            type = "sensor",
            commonR.string.basic_sensor_name_weight,
            commonR.string.sensor_description_weight,
            "mdi:scale-bathroom",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            deviceClass = "weight",
        )

        private val sensorPermissionMap = mapOf(
            activeCaloriesBurned.id to ActiveCaloriesBurnedRecord::class,
            basalBodyTemperature.id to BasalBodyTemperatureRecord::class,
            basalMetabolicRate.id to BasalMetabolicRateRecord::class,
            bloodGlucose.id to BloodGlucoseRecord::class,
            bodyFat.id to BodyFatRecord::class,
            bodyWaterMass.id to BodyWaterMassRecord::class,
            bodyTemperature.id to BodyTemperatureRecord::class,
            boneMass.id to BoneMassRecord::class,
            diastolicBloodPressure.id to BloodPressureRecord::class,
            distance.id to DistanceRecord::class,
            elevationGained.id to ElevationGainedRecord::class,
            floorsClimbed.id to FloorsClimbedRecord::class,
            heartRate.id to HeartRateRecord::class,
            heartRateVariability.id to HeartRateVariabilityRmssdRecord::class,
            height.id to HeightRecord::class,
            hydration.id to HydrationRecord::class,
            leanBodyMass.id to LeanBodyMassRecord::class,
            oxygenSaturation.id to OxygenSaturationRecord::class,
            respiratoryRate.id to RespiratoryRateRecord::class,
            restingHeartRate.id to RestingHeartRateRecord::class,
            sleepDuration.id to SleepSessionRecord::class,
            steps.id to StepsRecord::class,
            systolicBloodPressure.id to BloodPressureRecord::class,
            totalCaloriesBurned.id to TotalCaloriesBurnedRecord::class,
            vo2Max.id to Vo2MaxRecord::class,
            weight.id to WeightRecord::class,
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_health_connect

    override fun requiredPermissions(sensorId: String): Array<String> {
        return FailFast.failOnCatch({ "Unable to get required permissions for $sensorId" }, emptyArray<String>()) {
            val permissions = sensorPermissionMap[sensorId]?.let { recordClass ->
                val readPermission = HealthPermission.getReadPermission(recordClass)
                if (getOrCreateHealthConnectClient()?.features
                        ?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
                    == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                ) {
                    arrayOf(readPermission, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
                } else {
                    arrayOf(readPermission)
                }
            }
            FailFast.failWhen(permissions == null) { "Missing sensor mapping for $sensorId" }
            permissions ?: emptyArray()
        }
    }

    override suspend fun requestSensorUpdate() {
        if (isEnabled(activeCaloriesBurned)) {
            updateActiveCaloriesBurnedSensor()
        }
        if (isEnabled(basalBodyTemperature)) {
            updateBasalBodyTemperatureSensor()
        }
        if (isEnabled(basalMetabolicRate)) {
            updateBasalMetabolicRateSensor()
        }
        if (isEnabled(bloodGlucose)) {
            updateBloodGlucoseSensor()
        }
        if (isEnabled(bodyFat)) {
            updateBodyFatSensor()
        }
        if (isEnabled(bodyWaterMass)) {
            updateBodyWaterMassSensor()
        }
        if (isEnabled(bodyTemperature)) {
            updateBodyTemperatureSensor()
        }
        if (isEnabled(boneMass)) {
            updateBoneMassSensor()
        }
        if (isEnabled(diastolicBloodPressure)) {
            updateBloodPressureSensors(true)
        }
        if (isEnabled(distance)) {
            updateDistanceSensor()
        }
        if (isEnabled(elevationGained)) {
            updateElevationGainedSensor()
        }
        if (isEnabled(floorsClimbed)) {
            updateFloorsClimbedSensor()
        }
        if (isEnabled(heartRate)) {
            updateHeartRateSensor()
        }
        if (isEnabled(heartRateVariability)) {
            updateHeartRateVariabilitySensor()
        }
        if (isEnabled(height)) {
            updateHeightSensor()
        }
        if (isEnabled(hydration)) {
            updateHydrationSensor()
        }
        if (isEnabled(leanBodyMass)) {
            updateLeanBodyMassSensor()
        }
        if (isEnabled(oxygenSaturation)) {
            updateOxygenSaturationSensor()
        }
        if (isEnabled(respiratoryRate)) {
            updateRespiratoryRateSensor()
        }
        if (isEnabled(restingHeartRate)) {
            updateRestingHeartRateSensor()
        }
        if (isEnabled(sleepDuration)) {
            updateSleepDurationSensor()
        }
        if (isEnabled(steps)) {
            updateStepsSensor()
        }
        if (isEnabled(systolicBloodPressure)) {
            updateBloodPressureSensors(false)
        }
        if (isEnabled(totalCaloriesBurned)) {
            updateTotalCaloriesBurnedSensor()
        }
        if (isEnabled(vo2Max)) {
            updateVo2MaxSensor()
        }
        if (isEnabled(weight)) {
            updateWeightSensor()
        }
    }

    private suspend fun updateActiveCaloriesBurnedSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val activeCaloriesBurnedRequest = buildReadRecordsRequest(ActiveCaloriesBurnedRecord::class)
        val response = healthConnectClient.readRecordsOrNull(activeCaloriesBurnedRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            activeCaloriesBurned,
            BigDecimal(response.records.last().energy.inKilocalories).setScale(2, RoundingMode.HALF_EVEN),
            activeCaloriesBurned.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBasalBodyTemperatureSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val basalBodyTemperatureRequest = buildReadRecordsRequest(BasalBodyTemperatureRecord::class)
        val response = healthConnectClient.readRecordsOrNull(basalBodyTemperatureRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            basalBodyTemperature,
            response.records.last().temperature.inCelsius,
            basalBodyTemperature.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBasalMetabolicRateSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val basalMetabolicRateRequest = buildReadRecordsRequest(BasalMetabolicRateRecord::class)
        val response = healthConnectClient.readRecordsOrNull(basalMetabolicRateRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            basalMetabolicRate,
            response.records.last().basalMetabolicRate.inKilocaloriesPerDay,
            basalMetabolicRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBloodGlucoseSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val bloodGlucoseRequest = buildReadRecordsRequest(BloodGlucoseRecord::class)
        val response = healthConnectClient.readRecordsOrNull(bloodGlucoseRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            bloodGlucose,
            response.records.last().level.inMilligramsPerDeciliter,
            bloodGlucose.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "mealType" to getMealType(response.records.last().mealType),
                "relationToMeal" to getRelationToMeal(response.records.last().relationToMeal),
                "specimenSource" to getSpecimenSource(response.records.last().specimenSource),
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBloodPressureSensors(isDiastolic: Boolean) {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val bloodPressureRequest = buildReadRecordsRequest(BloodPressureRecord::class)
        val response = healthConnectClient.readRecordsOrNull(bloodPressureRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            if (isDiastolic) diastolicBloodPressure else systolicBloodPressure,
            if (isDiastolic) {
                response.records.last().diastolic.inMillimetersOfMercury
            } else {
                response.records.last().systolic.inMillimetersOfMercury
            },
            if (isDiastolic) diastolicBloodPressure.statelessIcon else systolicBloodPressure.statelessIcon,
            attributes = mapOf(
                "bodyPosition" to getBloodPressureBodyPosition(response.records.last().bodyPosition),
                "date" to response.records.last().time,
                "measurementLocation" to
                    getBloodPressureMeasurementLocation(response.records.last().measurementLocation),
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBodyFatSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val bodyFatRequest = buildReadRecordsRequest(BodyFatRecord::class)
        val response = healthConnectClient.readRecordsOrNull(bodyFatRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            bodyFat,
            BigDecimal(response.records.last().percentage.value).setScale(2, RoundingMode.HALF_EVEN),
            bodyFat.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBodyWaterMassSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val bodyWaterMassRequest = buildReadRecordsRequest(BodyWaterMassRecord::class)
        val response = healthConnectClient.readRecordsOrNull(bodyWaterMassRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            bodyWaterMass,
            response.records.last().mass.inGrams,
            bodyWaterMass.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBodyTemperatureSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val bodyTemperatureRequest = buildReadRecordsRequest(BodyTemperatureRecord::class)
        val response = healthConnectClient.readRecordsOrNull(bodyTemperatureRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            bodyTemperature,
            response.records.last().temperature.inCelsius,
            bodyTemperature.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "measurementLocation" to
                    getBodyTemperatureMeasurementLocation(response.records.last().measurementLocation),
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateBoneMassSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val boneMassRequest = buildReadRecordsRequest(BoneMassRecord::class)
        val response = healthConnectClient.readRecordsOrNull(boneMassRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            boneMass,
            response.records.last().mass.inGrams,
            boneMass.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateDistanceSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val distanceRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(DistanceRecord.DISTANCE_TOTAL)) ?: return
        val distanceTotal = distanceRequest[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0
        onSensorUpdated(
            distance,
            distanceTotal,
            distance.statelessIcon,
            attributes = buildAggregationAttributes(distanceRequest),
        )
    }

    private suspend fun updateElevationGainedSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val elevationGainedRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(ElevationGainedRecord.ELEVATION_GAINED_TOTAL))
                ?: return
        val elevationValue = elevationGainedRequest[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0
        onSensorUpdated(
            elevationGained,
            elevationValue,
            elevationGained.statelessIcon,
            attributes = buildAggregationAttributes(elevationGainedRequest),
        )
    }

    private suspend fun updateFloorsClimbedSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val floorsClimbedRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL))
                ?: return
        val floors = floorsClimbedRequest[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0
        onSensorUpdated(
            floorsClimbed,
            floors,
            floorsClimbed.statelessIcon,
            attributes = buildAggregationAttributes(floorsClimbedRequest),
        )
    }

    private suspend fun updateHeartRateSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val heartRateRequest = buildReadRecordsRequest(HeartRateRecord::class)
        val response = healthConnectClient.readRecordsOrNull(heartRateRequest)
        if (response == null || response.records.isEmpty() || response.records.last().samples.isEmpty()) {
            return
        }
        onSensorUpdated(
            heartRate,
            response.records.last().samples.last().beatsPerMinute,
            heartRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().samples.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateHeartRateVariabilitySensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val heartRateVariabilityRequest = buildReadRecordsRequest(HeartRateVariabilityRmssdRecord::class)
        val response = healthConnectClient.readRecordsOrNull(heartRateVariabilityRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            heartRateVariability,
            response.records.last().heartRateVariabilityMillis,
            heartRateVariability.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateHeightSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val heightRequest = buildReadRecordsRequest(HeightRecord::class)
        val response = healthConnectClient.readRecordsOrNull(heightRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            height,
            response.records.last().height.inMeters,
            height.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateHydrationSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val hydrationRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(HydrationRecord.VOLUME_TOTAL)) ?: return
        val hydrationTotal = hydrationRequest[HydrationRecord.VOLUME_TOTAL]?.inMilliliters ?: 0
        onSensorUpdated(
            hydration,
            hydrationTotal,
            hydration.statelessIcon,
            attributes = buildAggregationAttributes(hydrationRequest),
        )
    }

    private suspend fun updateLeanBodyMassSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val leanBodyMassRequest = buildReadRecordsRequest(LeanBodyMassRecord::class)
        val response = healthConnectClient.readRecordsOrNull(leanBodyMassRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            leanBodyMass,
            response.records.last().mass.inGrams,
            leanBodyMass.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateOxygenSaturationSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val oxygenSaturationRequest = buildReadRecordsRequest(OxygenSaturationRecord::class)
        val response = healthConnectClient.readRecordsOrNull(oxygenSaturationRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            oxygenSaturation,
            response.records.last().percentage.value,
            oxygenSaturation.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateRespiratoryRateSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val respiratoryRateRequest = buildReadRecordsRequest(RespiratoryRateRecord::class)
        val response = healthConnectClient.readRecordsOrNull(respiratoryRateRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            respiratoryRate,
            response.records.last().rate,
            respiratoryRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateRestingHeartRateSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val restingHeartRateRequest = buildReadRecordsRequest(RestingHeartRateRecord::class)
        val response = healthConnectClient.readRecordsOrNull(restingHeartRateRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            restingHeartRate,
            response.records.last().beatsPerMinute,
            restingHeartRate.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateSleepDurationSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val sleepRequest = buildReadRecordsRequest(SleepSessionRecord::class)
        val sleepRecords = healthConnectClient.readRecordsOrNull(sleepRequest)
        if (sleepRecords == null || sleepRecords.records.isEmpty()) {
            return
        }
        val lastSleepRecord = sleepRecords.records.last()
        val sleepRecordDuration = calculateSleepDurationInMinutes(lastSleepRecord.stages)
        onSensorUpdated(
            sleepDuration,
            sleepRecordDuration,
            sleepDuration.statelessIcon,
            attributes = mapOf(
                "endTime" to lastSleepRecord.endTime,
                "startTime" to lastSleepRecord.startTime,
                "sources" to lastSleepRecord.metadata.dataOrigin.packageName,
            ),
        )
    }

    /** @return Sleep duration based on the stages, excluding awake (+ in bed) and out of bed */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun calculateSleepDurationInMinutes(stages: List<SleepSessionRecord.Stage>): Long = stages
        .filter {
            it.stage != SleepSessionRecord.STAGE_TYPE_AWAKE &&
                it.stage != SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED &&
                it.stage != SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        }
        .sumOf { Duration.between(it.startTime, it.endTime).seconds }
        .toDuration(DurationUnit.SECONDS)
        // Don't convert to minutes until the end to avoid losing detail
        .inWholeMinutes

    private suspend fun updateStepsSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val stepsRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(StepsRecord.COUNT_TOTAL)) ?: return
        val totalSteps = stepsRequest[StepsRecord.COUNT_TOTAL] ?: 0
        onSensorUpdated(
            steps,
            totalSteps,
            steps.statelessIcon,
            attributes = buildAggregationAttributes(stepsRequest),
        )
    }

    private suspend fun updateTotalCaloriesBurnedSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val totalCaloriesBurnedRequest =
            healthConnectClient.aggregateOrNull(buildAggregationRequest(TotalCaloriesBurnedRecord.ENERGY_TOTAL))
                ?: return
        val energy = totalCaloriesBurnedRequest[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        onSensorUpdated(
            totalCaloriesBurned,
            BigDecimal(energy).setScale(2, RoundingMode.HALF_EVEN),
            totalCaloriesBurned.statelessIcon,
            attributes = buildAggregationAttributes(totalCaloriesBurnedRequest),
        )
    }

    private suspend fun updateVo2MaxSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val vo2MaxRequest = buildReadRecordsRequest(Vo2MaxRecord::class)
        val response = healthConnectClient.readRecordsOrNull(vo2MaxRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            vo2Max,
            response.records.last().vo2MillilitersPerMinuteKilogram,
            vo2Max.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "measurementMethod" to getMeasurementMethod(response.records.last().measurementMethod),
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    private suspend fun updateWeightSensor() {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return
        val weightRequest = buildReadRecordsRequest(WeightRecord::class)
        val response = healthConnectClient.readRecordsOrNull(weightRequest)
        if (response == null || response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            weight,
            BigDecimal(response.records.last().weight.inGrams).setScale(2, RoundingMode.HALF_EVEN),
            weight.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName,
            ),
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#health-connect-sensors"
    }

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return if (hasSensor()) {
            listOf(
                activeCaloriesBurned,
                basalBodyTemperature,
                basalMetabolicRate,
                bloodGlucose,
                bodyFat,
                bodyTemperature,
                bodyWaterMass,
                boneMass,
                diastolicBloodPressure,
                distance,
                elevationGained,
                floorsClimbed,
                heartRate,
                heartRateVariability,
                height,
                hydration,
                leanBodyMass,
                oxygenSaturation,
                respiratoryRate,
                restingHeartRate,
                sleepDuration,
                steps,
                systolicBloodPressure,
                totalCaloriesBurned,
                vo2Max,
                weight,
            )
        } else {
            emptyList()
        }
    }

    override fun hasSensor(): Boolean {
        return HealthConnectClient.getSdkStatus(applicationContext) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun checkPermission(sensorId: String): Boolean {
        val healthConnectClient = getOrCreateHealthConnectClient() ?: return false
        return try {
            healthConnectClient.permissionController.getGrantedPermissions().containsAll(
                requiredPermissions(sensorId).toSet(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Unable to check permissions")
            true // default to true as we still need to check sensor enabled state
        }
    }

    private fun getOrCreateHealthConnectClient(): HealthConnectClient? {
        return try {
            HealthConnectClient.getOrCreate(applicationContext)
        } catch (e: RuntimeException) {
            Timber.e(e, "Unable to create Health Connect client")
            null
        }
    }

    private suspend fun <T : Record> HealthConnectClient.readRecordsOrNull(
        request: ReadRecordsRequest<T>,
    ): ReadRecordsResponse<T>? = try {
        readRecords(request)
    } catch (e: Exception) {
        Timber.e(e, "Could not read records")
        null
    }

    private suspend fun HealthConnectClient.aggregateOrNull(request: AggregateRequest): AggregationResult? = try {
        aggregate(request)
    } catch (e: Exception) {
        Timber.e(e, "Could not aggregate")
        null
    }

    private fun buildAggregationRequest(metric: AggregateMetric<*>): AggregateRequest {
        return AggregateRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(
                LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                LocalDateTime.of(LocalDate.now(), LocalTime.MAX),
            ),
        )
    }

    private fun <T : Record> buildReadRecordsRequest(request: KClass<T>): ReadRecordsRequest<T> {
        return ReadRecordsRequest(
            recordType = request,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now(),
            ),
            ascendingOrder = false,
            pageSize = 1,
        )
    }

    private fun buildAggregationAttributes(result: AggregationResult): Map<String, Any> {
        return mapOf(
            "endTime" to Instant.now(),
            "sources" to result.dataOrigins.map { it.packageName },
        )
    }

    private fun getRelationToMeal(relation: Int): String {
        return when (relation) {
            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before_meal"
            BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "general"
            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after_meal"
            BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getSpecimenSource(source: Int): String {
        return when (source) {
            BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "capillary_blood"
            BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "interstitial_fluid"
            BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "plasma"
            BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "serum"
            BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "tears"
            BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN -> STATE_UNKNOWN
            BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "whole_blood"
            else -> STATE_UNKNOWN
        }
    }

    private fun getMealType(mealType: Int): String {
        return when (mealType) {
            MealType.MEAL_TYPE_BREAKFAST -> "breakfast"
            MealType.MEAL_TYPE_LUNCH -> "lunch"
            MealType.MEAL_TYPE_DINNER -> "dinner"
            MealType.MEAL_TYPE_SNACK -> "snack"
            MealType.MEAL_TYPE_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getBloodPressureBodyPosition(position: Int): String {
        return when (position) {
            BloodPressureRecord.BODY_POSITION_LYING_DOWN -> "lying_down"
            BloodPressureRecord.BODY_POSITION_RECLINING -> "reclining"
            BloodPressureRecord.BODY_POSITION_SITTING_DOWN -> "sitting_down"
            BloodPressureRecord.BODY_POSITION_STANDING_UP -> "standing_up"
            BloodPressureRecord.BODY_POSITION_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getBloodPressureMeasurementLocation(location: Int): String {
        return when (location) {
            BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST -> "left_wrist"
            BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM -> "left_upper_arm"
            BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST -> "right_wrist"
            BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM -> "right_upper_arm"
            BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getMeasurementMethod(method: Int): String {
        return when (method) {
            Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST -> "cooper_test"
            Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO -> "heart_rate_ratio"
            Vo2MaxRecord.MEASUREMENT_METHOD_METABOLIC_CART -> "metabolic_cart"
            Vo2MaxRecord.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST -> "multistage_fitness_test"
            Vo2MaxRecord.MEASUREMENT_METHOD_OTHER -> "other"
            Vo2MaxRecord.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST -> "rockport_fitness_test"
            else -> STATE_UNKNOWN
        }
    }

    private fun getBodyTemperatureMeasurementLocation(location: Int): String {
        return when (location) {
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT -> "armpit"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR -> "ear"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FINGER -> "finger"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FOREHEAD -> "forehead"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH -> "mouth"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_RECTUM -> "rectum"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TEMPORAL_ARTERY -> "temporal_artery"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TOE -> "toe"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN -> STATE_UNKNOWN
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_VAGINA -> "vagina"
            BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST -> "wrist"
            else -> STATE_UNKNOWN
        }
    }
}
