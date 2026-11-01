package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HealthConnectSensorManagerTest {

    private val context = mockk<Context> {
        every { applicationContext } returns this
    }

    private val sensorRepository = mockk<SensorRepository>(relaxed = true)
    private val sensorManager = HealthConnectSensorManager(context, sensorRepository, mockk())
    private val healthConnectClient = mockk<HealthConnectClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(HealthConnectClient.Companion)
        every { HealthConnectClient.getOrCreate(any()) } returns healthConnectClient
    }

    @Test
    fun `Given disabled sensors when updating then permissions are queried once`() = runTest {
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns emptySet()

        sensorManager.requestSensorUpdate()

        coVerify(exactly = 1) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 0) { healthConnectClient.readRecords(any<ReadRecordsRequest<Record>>()) }
        coVerify(exactly = 0) { healthConnectClient.aggregate(any()) }
    }

    @Test
    fun `Given granted permissions but disabled sensors when updating then no health data is read`() = runTest {
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns
            setOf(HealthPermission.getReadPermission(StepsRecord::class))

        sensorManager.requestSensorUpdate()

        coVerify(exactly = 1) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 1) { sensorRepository.get(HealthConnectSensorManager.steps.id) }
        coVerify(exactly = 0) { healthConnectClient.readRecords(any<ReadRecordsRequest<Record>>()) }
        coVerify(exactly = 0) { healthConnectClient.aggregate(any()) }
    }

    @Test
    fun `Given revoked permissions when updating again then grants are refreshed before reading`() = runTest {
        enableSensor(HealthConnectSensorManager.steps)
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returnsMany listOf(
            setOf(HealthPermission.getReadPermission(StepsRecord::class)),
            emptySet(),
        )
        coEvery { healthConnectClient.aggregate(any()) } throws SecurityException("Read permission revoked")

        sensorManager.requestSensorUpdate()
        sensorManager.requestSensorUpdate()

        coVerify(exactly = 2) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 1) { healthConnectClient.aggregate(any()) }
        coVerify(exactly = 0) { healthConnectClient.readRecords(any<ReadRecordsRequest<Record>>()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given a missing required permission when updating then enabled sensor is not read`(
        missingBackgroundPermission: Boolean,
    ) = runTest {
        enableSensor(HealthConnectSensorManager.steps)
        every {
            healthConnectClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        } returns HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val grantedPermission = if (missingBackgroundPermission) {
            HealthPermission.getReadPermission(StepsRecord::class)
        } else {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        }
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns setOf(grantedPermission)

        sensorManager.requestSensorUpdate()

        coVerify(exactly = 1) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 0) { healthConnectClient.aggregate(any()) }
    }

    @Test
    fun `Given permission query failure when updating then enabled state is checked and next update retries`() = runTest {
        enableSensor(HealthConnectSensorManager.steps)
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } throws
            IllegalStateException("Permission service unavailable")
        coEvery { healthConnectClient.aggregate(any()) } throws SecurityException("Read denied")

        sensorManager.requestSensorUpdate()
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns emptySet()
        sensorManager.requestSensorUpdate()

        coVerify(exactly = 2) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 1) { healthConnectClient.aggregate(any()) }
        coVerify(exactly = 0) { healthConnectClient.readRecords(any<ReadRecordsRequest<Record>>()) }
    }

    @Test
    fun `Given standalone checks when permissions change or query fails then each check stays fresh`() = runTest {
        val sensorId = HealthConnectSensorManager.steps.id
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returnsMany listOf(
            setOf(HealthPermission.getReadPermission(StepsRecord::class)),
            emptySet(),
        )
        assertTrue(sensorManager.checkPermission(sensorId))
        assertFalse(sensorManager.checkPermission(sensorId))
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } throws
            IllegalStateException("Permission service unavailable")
        assertTrue(sensorManager.checkPermission(sensorId))
        coVerify(exactly = 3) { healthConnectClient.permissionController.getGrantedPermissions() }
    }

    @Test
    fun `Given unavailable client when checking and updating then permission is denied without reads`() = runTest {
        every { HealthConnectClient.getOrCreate(any()) } throws IllegalStateException("Client unavailable")

        assertFalse(sensorManager.checkPermission(HealthConnectSensorManager.steps.id))
        sensorManager.requestSensorUpdate()

        coVerify(exactly = 0) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 0) { sensorRepository.get(any<String>()) }
    }

    @Test
    fun `Given enabled sleep sensors when updating then nested checks share permissions and one read`() = runTest {
        enableSensor(HealthConnectSensorManager.sleepDuration)
        enableSensor(HealthConnectSensorManager.sleepDeepDuration)
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns
            setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val record = mockk<SleepSessionRecord>(relaxed = true) {
            every { startTime } returns midnight
            every { endTime } returns midnight.plus(1, ChronoUnit.HOURS)
            every { stages } returns emptyList()
        }
        coEvery { healthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns
            mockk<ReadRecordsResponse<SleepSessionRecord>> { every { records } returns listOf(record) }

        sensorManager.requestSensorUpdate()

        coVerify(exactly = 1) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 1) { healthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) }
        coVerify(exactly = 1) { sensorRepository.update(match { it.id == HealthConnectSensorManager.sleepDuration.id }) }
        coVerify(exactly = 1) { sensorRepository.update(match { it.id == HealthConnectSensorManager.sleepDeepDuration.id }) }
        coVerify(exactly = 2) { sensorRepository.update(any()) }
        coVerify(exactly = 0) { healthConnectClient.aggregate(any()) }
    }

    @Test
    fun `Given overlapping updates and standalone check when grants differ then snapshots are isolated`() = runTest {
        enableSensor(HealthConnectSensorManager.steps)
        val firstQuery = CompletableDeferred<Set<String>>()
        var queries = 0
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } coAnswers {
            if (queries++ == 0) firstQuery.await() else emptySet()
        }
        coEvery { healthConnectClient.aggregate(any()) } throws SecurityException("Read denied")
        val firstUpdate = launch(start = CoroutineStart.UNDISPATCHED) { sensorManager.requestSensorUpdate() }

        assertFalse(sensorManager.checkPermission(HealthConnectSensorManager.steps.id))
        sensorManager.requestSensorUpdate()
        firstQuery.complete(setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        firstUpdate.join()

        coVerify(exactly = 3) { healthConnectClient.permissionController.getGrantedPermissions() }
        coVerify(exactly = 1) { healthConnectClient.aggregate(any()) }
    }

    private fun enableSensor(sensor: SensorManager.BasicSensor) {
        coEvery { sensorRepository.get(sensor.id) } returns listOf(
            Sensor(id = sensor.id, serverId = 1, enabled = true, state = ""),
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given background read support when getting permission then includes background permission if appropriate`(
        available: Boolean,
    ) {
        mockkObject(healthConnectClient.features)
        every {
            healthConnectClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        } returns
            if (available) {
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } else {
                HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
            }

        // Get permission(s) for any sensor - the actual sensor doesn't matter here
        val permissions = sensorManager.requiredPermissions(HealthConnectSensorManager.steps.id)
        assertEquals(
            available,
            permissions.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND),
        )
    }

    @Test
    fun `Given known Health Connect sleep stage types then constants are distinct`() {
        val expectedTypes = setOf(
            SleepSessionRecord.STAGE_TYPE_UNKNOWN,
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        )

        // Health Connect 1.1.0 does not expose a public list of valid stage types.
        // Review this set when upgrading the Health Connect dependency.
        assertEquals(8, expectedTypes.size)
    }

    @Test
    fun `Given sleep with stages when duration calculated then ignores awake (+ in bed) and out of bed stages`() {
        // Mock a sleep session with all possible stage types
        // Total duration: 12:00 AM-8:00 AM = 8 hours / 480 min
        // Asleep duration: 12:00 AM-4:00 AM + 5:00 AM-6:00 AM = 5 hours / 300 min
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val mockSleepStages = listOf(
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_SLEEPING
                every { startTime } returns midnight // 12:00 AM
                every { endTime } returns midnight.plus(1, ChronoUnit.HOURS) // 1:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_LIGHT
                every { startTime } returns midnight.plus(1, ChronoUnit.HOURS) // 1:00 AM
                every { endTime } returns midnight.plus(2, ChronoUnit.HOURS) // 2:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_DEEP
                every { startTime } returns midnight.plus(2, ChronoUnit.HOURS) // 2:00 AM
                every { endTime } returns midnight.plus(3, ChronoUnit.HOURS) // 3:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_REM
                every { startTime } returns midnight.plus(3, ChronoUnit.HOURS) // 3:00 AM
                every { endTime } returns midnight.plus(4, ChronoUnit.HOURS) // 4:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_AWAKE
                every { startTime } returns midnight.plus(4, ChronoUnit.HOURS) // 4:00 AM
                every { endTime } returns midnight.plus(5, ChronoUnit.HOURS) // 5:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_UNKNOWN
                every { startTime } returns midnight.plus(5, ChronoUnit.HOURS) // 5:00 AM
                every { endTime } returns midnight.plus(6, ChronoUnit.HOURS) // 6:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED
                every { startTime } returns midnight.plus(6, ChronoUnit.HOURS) // 6:00 AM
                every { endTime } returns midnight.plus(7, ChronoUnit.HOURS) // 7:00 AM
            },
            mockk<SleepSessionRecord.Stage> {
                every { stage } returns SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                every { startTime } returns midnight.plus(7, ChronoUnit.HOURS) // 7:00 AM
                every { endTime } returns midnight.plus(8, ChronoUnit.HOURS) // 8:00 AM
            },
        )

        val result = sensorManager.analyzeSleepStages(mockSleepStages).sleepDuration
        assertEquals(300.minutes, result)
    }

    @Test
    fun `Given repeated sleep stages when analyzed then durations are summed by stage type`() {
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val stages = listOf(
            SleepSessionRecord.Stage(
                startTime = midnight,
                endTime = midnight.plus(30, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(30, ChronoUnit.MINUTES),
                endTime = midnight.plus(90, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_DEEP,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(90, ChronoUnit.MINUTES),
                endTime = midnight.plus(150, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(150, ChronoUnit.MINUTES),
                endTime = midnight.plus(180, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_REM,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(180, ChronoUnit.MINUTES),
                endTime = midnight.plus(195, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_AWAKE,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(195, ChronoUnit.MINUTES),
                endTime = midnight.plus(205, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(205, ChronoUnit.MINUTES),
                endTime = midnight.plus(215, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(215, ChronoUnit.MINUTES),
                endTime = midnight.plus(245, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_SLEEPING,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(245, ChronoUnit.MINUTES),
                endTime = midnight.plus(260, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_UNKNOWN,
            ),
        )

        val result = sensorManager.analyzeSleepStages(stages)

        assertEquals(90.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_LIGHT])
        assertEquals(60.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_DEEP])
        assertEquals(30.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_REM])
        assertEquals(15.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_AWAKE])
        assertEquals(10.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED])
        assertEquals(10.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_OUT_OF_BED])
        assertEquals(30.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_SLEEPING])
        assertEquals(15.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_UNKNOWN])
        assertEquals(225.minutes, result.sleepDuration)
    }

    @Test
    fun `Given a missing sleep stage when analyzed then no duration is created for that stage`() {
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val stages = listOf(
            SleepSessionRecord.Stage(
                startTime = midnight,
                endTime = midnight.plus(1, ChronoUnit.HOURS),
                stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
            ),
        )

        val result = sensorManager.analyzeSleepStages(stages)

        assertFalse(result.durationByStage.containsKey(SleepSessionRecord.STAGE_TYPE_DEEP))
    }

    @Test
    fun `Given timeline disabled when analyzing sleep stages then durations remain available`() {
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val stages = listOf(
            SleepSessionRecord.Stage(
                startTime = midnight,
                endTime = midnight.plus(30, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(30, ChronoUnit.MINUTES),
                endTime = midnight.plus(60, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_DEEP,
            ),
        )

        val result = sensorManager.analyzeSleepStages(stages, includeTimeline = false)

        assertEquals(60.minutes, result.sleepDuration)
        assertEquals(30.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_LIGHT])
        assertEquals(30.minutes, result.durationByStage[SleepSessionRecord.STAGE_TYPE_DEEP])
        assertEquals(emptyList<String>(), result.stageTypes)
        assertEquals(emptyList<Instant>(), result.stageStartTimes)
        assertEquals(emptyList<Instant>(), result.stageEndTimes)
    }

    @Test
    fun `Given sleep stages when analyzed then complete ordered timeline is preserved as primitive lists`() {
        val midnight = Instant.parse("2026-07-01T00:00:00Z")
        val stages = listOf(
            SleepSessionRecord.Stage(
                startTime = midnight,
                endTime = midnight.plus(30, ChronoUnit.MINUTES),
                stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
            ),
            SleepSessionRecord.Stage(
                startTime = midnight.plus(30, ChronoUnit.MINUTES),
                endTime = midnight.plus(1, ChronoUnit.HOURS),
                stage = SleepSessionRecord.STAGE_TYPE_DEEP,
            ),
        )

        val result = sensorManager.analyzeSleepStages(stages)

        assertEquals(listOf("light", "deep"), result.stageTypes)
        assertEquals(
            listOf(
                midnight,
                midnight.plus(30, ChronoUnit.MINUTES),
            ),
            result.stageStartTimes,
        )
        assertEquals(
            listOf(
                midnight.plus(30, ChronoUnit.MINUTES),
                midnight.plus(1, ChronoUnit.HOURS),
            ),
            result.stageEndTimes,
        )
    }
}
