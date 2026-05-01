package io.homeassistant.companion.android.sensors.healthconnect.command

import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectWriteRepository
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectWriteResult
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import timber.log.Timber

/**
 * Translates `command_health_connect_write` FCM payloads into typed
 * [HealthConnectWriteRepository] calls.
 *
 * Lives separately from [io.homeassistant.companion.android.notifications.MessagingManager] to
 * keep that already-large class from growing further and to make the parsing logic unit-testable
 * without standing up the messaging stack.
 *
 * The handler never throws: parse / dispatch failures are returned as
 * [HealthConnectWriteResult] so [MessagingManager] can decide whether to surface a notification,
 * log, or both. Raw payload values (which may be PHI like weights or glucose levels) are
 * deliberately omitted from log messages — only the data type key and outcome are recorded.
 */
class HealthConnectWriteCommandHandler @Inject constructor(
    private val repository: HealthConnectWriteRepository,
    @OptIn(ExperimentalTime::class)
    private val clock: Clock,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun handle(data: Map<String, String>): HealthConnectWriteResult {
        val now = clock.now().toJavaInstant()
        val payload = try {
            HealthConnectWriteCommandPayload.parse(data, now)
        } catch (e: HealthConnectWriteCommandPayload.InvalidPayloadException) {
            Timber.w("Rejecting health_connect_write payload: ${e.message}")
            return HealthConnectWriteResult.InvalidPayload(e.message ?: "Invalid payload")
        }
        val result = dispatch(payload)
        when (result) {
            is HealthConnectWriteResult.Success -> Timber.d(
                "Wrote ${payload.dataType.key} (${result.insertedIds.size} record(s))",
            )
            is HealthConnectWriteResult.MissingPermission -> Timber.w(
                "health_connect_write blocked, missing permission: ${result.permission}",
            )
            is HealthConnectWriteResult.Unavailable -> Timber.w(
                "health_connect_write skipped — Health Connect unavailable on this device",
            )
            is HealthConnectWriteResult.InvalidPayload -> Timber.w(
                "health_connect_write rejected: ${result.reason}",
            )
            is HealthConnectWriteResult.Failure -> Timber.w(
                result.cause,
                "health_connect_write failed for ${payload.dataType.key}",
            )
        }
        return result
    }

    private suspend fun dispatch(payload: HealthConnectWriteCommandPayload): HealthConnectWriteResult = when (payload) {
        is HealthConnectWriteCommandPayload.Instantaneous -> dispatchInstantaneous(payload)
        is HealthConnectWriteCommandPayload.Interval -> dispatchInterval(payload)
        is HealthConnectWriteCommandPayload.BloodPressure -> repository.writeBloodPressure(
            time = payload.time,
            systolicMmHg = payload.systolic,
            diastolicMmHg = payload.diastolic,
            clientRecordId = payload.clientRecordId,
        )
        is HealthConnectWriteCommandPayload.HeartRate -> repository.writeHeartRate(
            startTime = payload.startTime,
            endTime = payload.endTime,
            samples = payload.samples,
            clientRecordId = payload.clientRecordId,
        )
        is HealthConnectWriteCommandPayload.Sleep -> repository.writeSleep(
            startTime = payload.startTime,
            endTime = payload.endTime,
            title = payload.title,
            notes = payload.notes,
            stages = payload.stages,
            clientRecordId = payload.clientRecordId,
        )
    }

    private suspend fun dispatchInstantaneous(
        payload: HealthConnectWriteCommandPayload.Instantaneous,
    ): HealthConnectWriteResult {
        val time: Instant = payload.time
        val crid = payload.clientRecordId
        val v = payload.value
        return when (payload.dataType) {
            HealthConnectDataType.BasalBodyTemperature ->
                repository.writeBasalBodyTemperature(time, v, crid)
            HealthConnectDataType.BasalMetabolicRate ->
                repository.writeBasalMetabolicRate(time, v, crid)
            HealthConnectDataType.BloodGlucose ->
                repository.writeBloodGlucose(time, v, crid)
            HealthConnectDataType.BodyFat ->
                repository.writeBodyFat(time, v, crid)
            HealthConnectDataType.BodyTemperature ->
                repository.writeBodyTemperature(time, v, crid)
            HealthConnectDataType.BodyWaterMass ->
                repository.writeBodyWaterMass(time, v, crid)
            HealthConnectDataType.BoneMass ->
                repository.writeBoneMass(time, v, crid)
            HealthConnectDataType.HeartRateVariability ->
                repository.writeHeartRateVariability(time, v, crid)
            HealthConnectDataType.Height ->
                repository.writeHeight(time, v, crid)
            HealthConnectDataType.LeanBodyMass ->
                repository.writeLeanBodyMass(time, v, crid)
            HealthConnectDataType.OxygenSaturation ->
                repository.writeOxygenSaturation(time, v, crid)
            HealthConnectDataType.RespiratoryRate ->
                repository.writeRespiratoryRate(time, v, crid)
            HealthConnectDataType.RestingHeartRate ->
                repository.writeRestingHeartRate(time, v.toLong(), crid)
            HealthConnectDataType.Vo2Max ->
                repository.writeVo2Max(time, v, crid)
            HealthConnectDataType.Weight ->
                repository.writeWeight(time, v, crid)
            else -> HealthConnectWriteResult.InvalidPayload(
                "Data type ${payload.dataType.key} is not instantaneous",
            )
        }
    }

    private suspend fun dispatchInterval(
        payload: HealthConnectWriteCommandPayload.Interval,
    ): HealthConnectWriteResult {
        val start = payload.startTime
        val end = payload.endTime
        val crid = payload.clientRecordId
        val v = payload.value
        return when (payload.dataType) {
            HealthConnectDataType.ActiveCaloriesBurned ->
                repository.writeActiveCaloriesBurned(start, end, v, crid)
            HealthConnectDataType.Distance ->
                repository.writeDistance(start, end, v, crid)
            HealthConnectDataType.ElevationGained ->
                repository.writeElevationGained(start, end, v, crid)
            HealthConnectDataType.FloorsClimbed ->
                repository.writeFloorsClimbed(start, end, v, crid)
            HealthConnectDataType.Hydration ->
                repository.writeHydration(start, end, v, crid)
            HealthConnectDataType.Steps ->
                repository.writeSteps(start, end, v.toLong(), crid)
            HealthConnectDataType.TotalCaloriesBurned ->
                repository.writeTotalCaloriesBurned(start, end, v, crid)
            else -> HealthConnectWriteResult.InvalidPayload(
                "Data type ${payload.dataType.key} is not interval",
            )
        }
    }
}
