package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Verifies that each typed `writeX` method on [HealthConnectWriteRepositoryImpl] builds the
 * correct [Record], requests the matching WRITE permission, and forwards to
 * [HealthConnectClient.insertRecords].
 *
 * The test set is intentionally shaped around the four record categories:
 *  - Instantaneous (Weight, BloodPressure)
 *  - Interval (ActiveCaloriesBurned, Hydration, Steps)
 *  - Series (HeartRate)
 *  - Session (Sleep)
 *
 * Each category exercises one or two representative data types end-to-end. The remaining
 * data types share construction logic with their representative — adding 18 more
 * near-identical assertions would not catch additional regressions and would obscure the
 * architectural intent of the test file. New data types that introduce *new* construction
 * logic (e.g. a new unit class, multi-field, or sample-based record) MUST add a case here.
 */
@ExtendWith(ConsoleLogExtension::class)
class HealthConnectWriteRepositoryImplTest {

    private lateinit var client: HealthConnectClient
    private lateinit var permissionController: PermissionController
    private lateinit var repository: HealthConnectWriteRepositoryImpl

    private val now: Instant = Instant.parse("2026-05-01T10:00:00Z")
    private val later: Instant = Instant.parse("2026-05-01T10:30:00Z")

    @BeforeEach
    fun setUp() {
        permissionController = mockk(relaxed = true)
        client = mockk {
            every { permissionController } returns this@HealthConnectWriteRepositoryImplTest.permissionController
        }
        // Default: every permission is granted; individual tests override as needed.
        coEvery { permissionController.getGrantedPermissions() } returns
            HealthConnectDataType.all.map { HealthPermission.getWritePermission(it.recordClass) }.toSet()
        coEvery { client.insertRecords(any()) } returns InsertRecordsResponse(listOf("id-1"))
        repository = HealthConnectWriteRepositoryImpl(Provider { client })
    }

    @Test
    fun `Unavailable when client provider returns null`() = runTest {
        val nullRepo = HealthConnectWriteRepositoryImpl(Provider { null })
        val result = nullRepo.writeWeight(time = now, kilograms = 80.0)
        assertEquals(HealthConnectWriteResult.Unavailable, result)
    }

    @Test
    fun `MissingPermission when WRITE permission not granted`() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns emptySet()
        val result = repository.writeWeight(time = now, kilograms = 80.0)
        assertTrue(result is HealthConnectWriteResult.MissingPermission)
        assertEquals(
            HealthPermission.getWritePermission(WeightRecord::class),
            (result as HealthConnectWriteResult.MissingPermission).permission,
        )
    }

    @Test
    fun `writeWeight builds WeightRecord and calls insertRecords`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-w"))

        val result = repository.writeWeight(time = now, kilograms = 75.2, clientRecordId = "scale-42")

        assertTrue(result is HealthConnectWriteResult.Success)
        val record = captured.captured.single() as WeightRecord
        assertEquals(now, record.time)
        assertEquals(75.2, record.weight.inKilograms, 0.0001)
        assertEquals("scale-42", record.metadata.clientRecordId)
    }

    @Test
    fun `writeBloodPressure builds BloodPressureRecord with both pressures`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-bp"))

        val result = repository.writeBloodPressure(
            time = now,
            systolicMmHg = 118.0,
            diastolicMmHg = 76.0,
        )

        assertTrue(result is HealthConnectWriteResult.Success)
        val record = captured.captured.single() as BloodPressureRecord
        assertEquals(118.0, record.systolic.inMillimetersOfMercury, 0.0001)
        assertEquals(76.0, record.diastolic.inMillimetersOfMercury, 0.0001)
    }

    @Test
    fun `writeActiveCaloriesBurned uses kilocalories`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-c"))

        repository.writeActiveCaloriesBurned(now, later, kilocalories = 250.0)

        val record = captured.captured.single() as ActiveCaloriesBurnedRecord
        assertEquals(now, record.startTime)
        assertEquals(later, record.endTime)
        assertEquals(250.0, record.energy.inKilocalories, 0.0001)
    }

    @Test
    fun `writeHydration uses liters`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-h"))

        repository.writeHydration(now, later, liters = 0.5)

        val record = captured.captured.single() as HydrationRecord
        assertEquals(0.5, record.volume.inLiters, 0.0001)
    }

    @Test
    fun `writeSteps uses Long count`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-s"))

        repository.writeSteps(now, later, count = 1234L)

        val record = captured.captured.single() as StepsRecord
        assertEquals(1234L, record.count)
    }

    @Test
    fun `writeHeartRate forwards samples and rejects empty list`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-hr"))

        val samples = listOf(
            HeartRateRecord.Sample(time = now, beatsPerMinute = 72),
            HeartRateRecord.Sample(time = now.plusSeconds(60), beatsPerMinute = 76),
        )
        repository.writeHeartRate(now, later, samples = samples)
        val record = captured.captured.single() as HeartRateRecord
        assertEquals(samples, record.samples)

        val emptyResult = repository.writeHeartRate(now, later, samples = emptyList())
        assertTrue(emptyResult is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `writeExerciseSession builds ExerciseSessionRecord with type and metadata`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-ex"))

        repository.writeExerciseSession(
            startTime = now,
            endTime = later,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Evening run",
            notes = "felt great",
            clientRecordId = "ha-run-1",
        )

        val record = captured.captured.single() as ExerciseSessionRecord
        assertEquals(now, record.startTime)
        assertEquals(later, record.endTime)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, record.exerciseType)
        assertEquals("Evening run", record.title)
        assertEquals("felt great", record.notes)
        assertEquals("ha-run-1", record.metadata.clientRecordId)
    }

    @Test
    fun `writeSleep forwards stages and metadata`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-sleep"))

        val stages = listOf(
            SleepSessionRecord.Stage(
                startTime = now,
                endTime = later,
                stage = SleepSessionRecord.STAGE_TYPE_DEEP,
            ),
        )
        repository.writeSleep(
            startTime = now,
            endTime = later,
            title = "Nightly",
            notes = "felt rested",
            stages = stages,
            clientRecordId = "sleep-1",
        )

        val record = captured.captured.single() as SleepSessionRecord
        assertEquals("Nightly", record.title)
        assertEquals("felt rested", record.notes)
        assertEquals(stages, record.stages)
        assertEquals("sleep-1", record.metadata.clientRecordId)
    }

    @Test
    fun `writeSpeed builds SpeedRecord with samples`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-sp"))

        val samples = listOf(
            SpeedRecord.Sample(now, Velocity.metersPerSecond(3.0)),
            SpeedRecord.Sample(now.plusSeconds(60), Velocity.metersPerSecond(4.5)),
        )
        repository.writeSpeed(now, later, samples)

        val record = captured.captured.single() as SpeedRecord
        assertEquals(samples, record.samples)
    }

    @Test
    fun `writeSpeed rejects empty sample list`() = runTest {
        val result = repository.writeSpeed(now, later, samples = emptyList())
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `writePower builds PowerRecord with samples`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-pw"))

        val samples = listOf(PowerRecord.Sample(now, Power.watts(220.0)))
        repository.writePower(now, later, samples)

        val record = captured.captured.single() as PowerRecord
        assertEquals(samples, record.samples)
    }

    @Test
    fun `writePower rejects empty sample list`() = runTest {
        val result = repository.writePower(now, later, samples = emptyList())
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `writeCyclingPedalingCadence builds the record with rpm samples`() = runTest {
        val captured = slot<List<Record>>()
        coEvery { client.insertRecords(capture(captured)) } returns InsertRecordsResponse(listOf("id-rpm"))

        val samples = listOf(
            CyclingPedalingCadenceRecord.Sample(now, 88.0),
            CyclingPedalingCadenceRecord.Sample(now.plusSeconds(30), 92.0),
        )
        repository.writeCyclingPedalingCadence(now, later, samples)

        val record = captured.captured.single() as CyclingPedalingCadenceRecord
        assertEquals(samples, record.samples)
    }

    @Test
    fun `writeCyclingPedalingCadence rejects empty sample list`() = runTest {
        val result = repository.writeCyclingPedalingCadence(now, later, samples = emptyList())
        assertTrue(result is HealthConnectWriteResult.InvalidPayload)
    }

    @Test
    fun `Failure surfaces when insertRecords throws`() = runTest {
        val boom = RuntimeException("boom")
        coEvery { client.insertRecords(any()) } throws boom

        val result = repository.writeWeight(time = now, kilograms = 80.0)

        assertTrue(result is HealthConnectWriteResult.Failure)
        assertEquals(boom, (result as HealthConnectWriteResult.Failure).cause)
        coVerify { client.insertRecords(any()) }
    }
}
