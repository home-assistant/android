package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import java.time.Instant

/**
 * Writes Health Connect records on behalf of HA → device automations.
 *
 * The interface exposes:
 *  - One typed `writeX` builder per supported data type so the command handler — and
 *    eventually a future scripting surface — can call into HC with strongly-typed
 *    primitives instead of building [Record] objects directly.
 *  - A single low-level [write] entry point used by the typed builders and by tests
 *    that want to construct a custom [Record].
 *
 * Implementations must:
 *  - Resolve the WRITE permission via [HealthConnectDataType.writePermission] and short-circuit
 *    with [HealthConnectWriteResult.MissingPermission] when the user has not granted it,
 *    rather than letting Health Connect throw `SecurityException`. Centralizing the check
 *    lets the handler emit a single, consistent notification.
 *  - Tolerate a `null` [androidx.health.connect.client.HealthConnectClient]
 *    (Hilt-provided when HC is unavailable on the device) by returning
 *    [HealthConnectWriteResult.Unavailable] without throwing.
 */
interface HealthConnectWriteRepository {

    /**
     * Persist [record] to Health Connect.
     *
     * The data type — and therefore the WRITE permission to check — is derived from
     * `record::class` via [HealthConnectDataType.fromKey] on the matching record class.
     * Callers should prefer the typed `writeX` methods below; this overload exists for
     * unit tests and future code paths that already hold a fully-built [Record].
     */
    suspend fun write(record: Record): HealthConnectWriteResult

    suspend fun writeActiveCaloriesBurned(
        startTime: Instant,
        endTime: Instant,
        kilocalories: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBasalBodyTemperature(
        time: Instant,
        celsius: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBasalMetabolicRate(
        time: Instant,
        kilocaloriesPerDay: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBloodGlucose(
        time: Instant,
        millimolesPerLiter: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBloodPressure(
        time: Instant,
        systolicMmHg: Double,
        diastolicMmHg: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBodyFat(
        time: Instant,
        percentage: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBodyTemperature(
        time: Instant,
        celsius: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBodyWaterMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeBoneMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeDistance(
        startTime: Instant,
        endTime: Instant,
        meters: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeElevationGained(
        startTime: Instant,
        endTime: Instant,
        meters: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeExerciseSession(
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
        title: String? = null,
        notes: String? = null,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeFloorsClimbed(
        startTime: Instant,
        endTime: Instant,
        floors: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeHeartRate(
        startTime: Instant,
        endTime: Instant,
        samples: List<HeartRateRecord.Sample>,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeHeartRateVariability(
        time: Instant,
        rmssdMillis: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeHeight(time: Instant, meters: Double, clientRecordId: String? = null): HealthConnectWriteResult

    suspend fun writeHydration(
        startTime: Instant,
        endTime: Instant,
        liters: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeLeanBodyMass(
        time: Instant,
        kilograms: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeOxygenSaturation(
        time: Instant,
        percentage: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeRespiratoryRate(
        time: Instant,
        breathsPerMinute: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeRestingHeartRate(
        time: Instant,
        beatsPerMinute: Long,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeSleep(
        startTime: Instant,
        endTime: Instant,
        title: String? = null,
        notes: String? = null,
        stages: List<SleepSessionRecord.Stage> = emptyList(),
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeSteps(
        startTime: Instant,
        endTime: Instant,
        count: Long,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeTotalCaloriesBurned(
        startTime: Instant,
        endTime: Instant,
        kilocalories: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeVo2Max(
        time: Instant,
        millilitersPerMinuteKilogram: Double,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeWeight(time: Instant, kilograms: Double, clientRecordId: String? = null): HealthConnectWriteResult

    suspend fun writeSpeed(
        startTime: Instant,
        endTime: Instant,
        samples: List<SpeedRecord.Sample>,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writePower(
        startTime: Instant,
        endTime: Instant,
        samples: List<PowerRecord.Sample>,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult

    suspend fun writeCyclingPedalingCadence(
        startTime: Instant,
        endTime: Instant,
        samples: List<CyclingPedalingCadenceRecord.Sample>,
        clientRecordId: String? = null,
    ): HealthConnectWriteResult
}
