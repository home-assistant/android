package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.HealthConnectClient
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
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Volume
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Default [HealthConnectWriteRepository]. All record-building lives here so the typed
 * `writeX` methods stay narrow and the unit tests don't need to wire SDK objects.
 *
 * The [HealthConnectClient] is injected through a [Provider] because Hilt provides
 * the client as nullable (HC may be unavailable on the device); resolving lazily lets
 * us return [HealthConnectWriteResult.Unavailable] without forcing every caller to
 * do its own null-check.
 */
@Singleton
class HealthConnectWriteRepositoryImpl @Inject constructor(
    private val clientProvider: Provider<HealthConnectClient?>,
) : HealthConnectWriteRepository {

    override suspend fun write(record: Record): HealthConnectWriteResult {
        val dataType = HealthConnectDataType.all.firstOrNull { it.recordClass == record::class }
            ?: return HealthConnectWriteResult.InvalidPayload(
                "Unsupported record type: ${record::class.simpleName}",
            )
        return insert(dataType, listOf(record))
    }

    override suspend fun writeActiveCaloriesBurned(
        startTime: Instant,
        endTime: Instant,
        kilocalories: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.ActiveCaloriesBurned,
        listOf(
            ActiveCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                energy = Energy.kilocalories(kilocalories),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBasalBodyTemperature(
        time: Instant,
        celsius: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BasalBodyTemperature,
        listOf(
            BasalBodyTemperatureRecord(
                time = time,
                zoneOffset = null,
                temperature = Temperature.celsius(celsius),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBasalMetabolicRate(
        time: Instant,
        kilocaloriesPerDay: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BasalMetabolicRate,
        listOf(
            BasalMetabolicRateRecord(
                time = time,
                zoneOffset = null,
                basalMetabolicRate = Power.kilocaloriesPerDay(kilocaloriesPerDay),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBloodGlucose(
        time: Instant,
        millimolesPerLiter: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BloodGlucose,
        listOf(
            BloodGlucoseRecord(
                time = time,
                zoneOffset = null,
                level = BloodGlucose.millimolesPerLiter(millimolesPerLiter),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN,
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBloodPressure(
        time: Instant,
        systolicMmHg: Double,
        diastolicMmHg: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BloodPressure,
        listOf(
            BloodPressureRecord(
                time = time,
                zoneOffset = null,
                systolic = Pressure.millimetersOfMercury(systolicMmHg),
                diastolic = Pressure.millimetersOfMercury(diastolicMmHg),
                bodyPosition = BloodPressureRecord.BODY_POSITION_UNKNOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBodyFat(
        time: Instant,
        percentage: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BodyFat,
        listOf(
            BodyFatRecord(
                time = time,
                zoneOffset = null,
                percentage = Percentage(percentage),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBodyTemperature(
        time: Instant,
        celsius: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BodyTemperature,
        listOf(
            BodyTemperatureRecord(
                time = time,
                zoneOffset = null,
                temperature = Temperature.celsius(celsius),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBodyWaterMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BodyWaterMass,
        listOf(
            BodyWaterMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(kilograms),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeBoneMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.BoneMass,
        listOf(
            BoneMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(kilograms),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeDistance(
        startTime: Instant,
        endTime: Instant,
        meters: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Distance,
        listOf(
            DistanceRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                distance = Length.meters(meters),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeElevationGained(
        startTime: Instant,
        endTime: Instant,
        meters: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.ElevationGained,
        listOf(
            ElevationGainedRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                elevation = Length.meters(meters),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeExerciseSession(
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int,
        title: String?,
        notes: String?,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.ExerciseSession,
        listOf(
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                metadata = metadata(clientRecordId),
                exerciseType = exerciseType,
                title = title,
                notes = notes,
            ),
        ),
    )

    override suspend fun writeFloorsClimbed(
        startTime: Instant,
        endTime: Instant,
        floors: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.FloorsClimbed,
        listOf(
            FloorsClimbedRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                floors = floors,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeHeartRate(
        startTime: Instant,
        endTime: Instant,
        samples: List<HeartRateRecord.Sample>,
        clientRecordId: String?,
    ): HealthConnectWriteResult {
        if (samples.isEmpty()) {
            return HealthConnectWriteResult.InvalidPayload("heart_rate requires at least one sample")
        }
        return insert(
            HealthConnectDataType.HeartRate,
            listOf(
                HeartRateRecord(
                    startTime = startTime,
                    startZoneOffset = null,
                    endTime = endTime,
                    endZoneOffset = null,
                    samples = samples,
                    metadata = metadata(clientRecordId),
                ),
            ),
        )
    }

    override suspend fun writeHeartRateVariability(
        time: Instant,
        rmssdMillis: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.HeartRateVariability,
        listOf(
            HeartRateVariabilityRmssdRecord(
                time = time,
                zoneOffset = null,
                heartRateVariabilityMillis = rmssdMillis,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeHeight(
        time: Instant,
        meters: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Height,
        listOf(
            HeightRecord(
                time = time,
                zoneOffset = null,
                height = Length.meters(meters),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeHydration(
        startTime: Instant,
        endTime: Instant,
        liters: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Hydration,
        listOf(
            HydrationRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                volume = Volume.liters(liters),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeLeanBodyMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.LeanBodyMass,
        listOf(
            LeanBodyMassRecord(
                time = time,
                zoneOffset = null,
                mass = Mass.kilograms(kilograms),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeOxygenSaturation(
        time: Instant,
        percentage: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.OxygenSaturation,
        listOf(
            OxygenSaturationRecord(
                time = time,
                zoneOffset = null,
                percentage = Percentage(percentage),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeRespiratoryRate(
        time: Instant,
        breathsPerMinute: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.RespiratoryRate,
        listOf(
            RespiratoryRateRecord(
                time = time,
                zoneOffset = null,
                rate = breathsPerMinute,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeRestingHeartRate(
        time: Instant,
        beatsPerMinute: Long,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.RestingHeartRate,
        listOf(
            RestingHeartRateRecord(
                time = time,
                zoneOffset = null,
                beatsPerMinute = beatsPerMinute,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeSleep(
        startTime: Instant,
        endTime: Instant,
        title: String?,
        notes: String?,
        stages: List<SleepSessionRecord.Stage>,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Sleep,
        listOf(
            SleepSessionRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                metadata = metadata(clientRecordId),
                title = title,
                notes = notes,
                stages = stages,
            ),
        ),
    )

    override suspend fun writeSteps(
        startTime: Instant,
        endTime: Instant,
        count: Long,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Steps,
        listOf(
            StepsRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                count = count,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeTotalCaloriesBurned(
        startTime: Instant,
        endTime: Instant,
        kilocalories: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.TotalCaloriesBurned,
        listOf(
            TotalCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = null,
                endTime = endTime,
                endZoneOffset = null,
                energy = Energy.kilocalories(kilocalories),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeVo2Max(
        time: Instant,
        millilitersPerMinuteKilogram: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Vo2Max,
        listOf(
            Vo2MaxRecord(
                time = time,
                zoneOffset = null,
                vo2MillilitersPerMinuteKilogram = millilitersPerMinuteKilogram,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeWeight(
        time: Instant,
        kilograms: Double,
        clientRecordId: String?,
    ): HealthConnectWriteResult = insert(
        HealthConnectDataType.Weight,
        listOf(
            WeightRecord(
                time = time,
                zoneOffset = null,
                weight = Mass.kilograms(kilograms),
                metadata = metadata(clientRecordId),
            ),
        ),
    )

    override suspend fun writeSpeed(
        startTime: Instant,
        endTime: Instant,
        samples: List<SpeedRecord.Sample>,
        clientRecordId: String?,
    ): HealthConnectWriteResult {
        if (samples.isEmpty()) {
            return HealthConnectWriteResult.InvalidPayload("speed requires at least one sample")
        }
        return insert(
            HealthConnectDataType.Speed,
            listOf(
                SpeedRecord(
                    startTime = startTime,
                    startZoneOffset = null,
                    endTime = endTime,
                    endZoneOffset = null,
                    samples = samples,
                    metadata = metadata(clientRecordId),
                ),
            ),
        )
    }

    override suspend fun writePower(
        startTime: Instant,
        endTime: Instant,
        samples: List<PowerRecord.Sample>,
        clientRecordId: String?,
    ): HealthConnectWriteResult {
        if (samples.isEmpty()) {
            return HealthConnectWriteResult.InvalidPayload("power requires at least one sample")
        }
        return insert(
            HealthConnectDataType.Power,
            listOf(
                PowerRecord(
                    startTime = startTime,
                    startZoneOffset = null,
                    endTime = endTime,
                    endZoneOffset = null,
                    samples = samples,
                    metadata = metadata(clientRecordId),
                ),
            ),
        )
    }

    override suspend fun writeCyclingPedalingCadence(
        startTime: Instant,
        endTime: Instant,
        samples: List<CyclingPedalingCadenceRecord.Sample>,
        clientRecordId: String?,
    ): HealthConnectWriteResult {
        if (samples.isEmpty()) {
            return HealthConnectWriteResult.InvalidPayload(
                "cycling_pedaling_cadence requires at least one sample",
            )
        }
        return insert(
            HealthConnectDataType.CyclingPedalingCadence,
            listOf(
                CyclingPedalingCadenceRecord(
                    startTime = startTime,
                    startZoneOffset = null,
                    endTime = endTime,
                    endZoneOffset = null,
                    samples = samples,
                    metadata = metadata(clientRecordId),
                ),
            ),
        )
    }

    private suspend fun insert(dataType: HealthConnectDataType, records: List<Record>): HealthConnectWriteResult {
        val client = clientProvider.get()
            ?: return HealthConnectWriteResult.Unavailable

        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrElse { error ->
            Timber.w(error, "Failed to read granted Health Connect permissions")
            return HealthConnectWriteResult.Failure(error)
        }
        val writePermission = HealthPermission.getWritePermission(dataType.recordClass)
        if (writePermission !in granted) {
            return HealthConnectWriteResult.MissingPermission(writePermission)
        }

        return withContext(Dispatchers.IO) {
            runCatching { client.insertRecords(records) }
                .fold(
                    onSuccess = { response -> HealthConnectWriteResult.Success(response.recordIdsList) },
                    onFailure = { error ->
                        Timber.w(error, "Health Connect insertRecords failed for ${dataType.key}")
                        HealthConnectWriteResult.Failure(error)
                    },
                )
        }
    }

    /**
     * Build a manual-entry [Metadata] tagged with [clientRecordId] when the caller supplied
     * one, otherwise an empty manual-entry record. Manual-entry is the right
     * `RECORDING_METHOD_*` for HA-driven writes since the value originated outside HC and
     * was not auto-captured by a sensor.
     */
    private fun metadata(clientRecordId: String?): Metadata = if (clientRecordId.isNullOrBlank()) {
        Metadata.manualEntry()
    } else {
        Metadata.manualEntry(clientRecordId = clientRecordId)
    }
}
