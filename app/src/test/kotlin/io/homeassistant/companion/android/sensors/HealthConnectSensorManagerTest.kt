package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.units.Energy
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    @Test
    fun `Given active calories from several sources when updating then publishes the aggregated daily total`() = runTest {
        val sensorSlot = slot<Sensor>()
        val attributesSlot = slot<List<Attribute>>()
        givenActiveCaloriesSensorEnabled(sensorSlot, attributesSlot)
        coEvery { healthConnectClient.aggregate(any<AggregateRequest>()) } returns
            aggregationResult(
                energy = Energy.kilocalories(437.6),
                origins = setOf("com.android.healthconnect.phone", "com.garmin.android.apps.connectmobile"),
            )

        sensorManager.requestSensorUpdate()

        assertEquals("437.60", sensorSlot.captured.state)
        assertEquals(SensorManager.STATE_CLASS_TOTAL_INCREASING, sensorSlot.captured.stateClass)
        val attributes = attributesSlot.captured.associateBy { it.name }
        assertNotNull(attributes["sources"])
        assertTrue(attributes.getValue("sources").value.contains("com.garmin.android.apps.connectmobile"))
        assertFalse(attributes.containsKey("source"), "Only the aggregate 'sources' attribute is expected")
        // The total must come from an aggregation and never from reading a single (newest) record.
        coVerify(exactly = 0) {
            healthConnectClient.readRecords(any<ReadRecordsRequest<ActiveCaloriesBurnedRecord>>())
        }
    }

    @Test
    fun `Given no active calories recorded today when updating then publishes zero`() = runTest {
        val sensorSlot = slot<Sensor>()
        givenActiveCaloriesSensorEnabled(sensorSlot, slot())
        coEvery { healthConnectClient.aggregate(any<AggregateRequest>()) } returns
            aggregationResult(energy = null, origins = emptySet())

        sensorManager.requestSensorUpdate()

        assertEquals("0.00", sensorSlot.captured.state)
    }

    @Test
    fun `Given the aggregation fails when updating active calories then the previous state is kept`() = runTest {
        givenActiveCaloriesSensorEnabled(slot(), slot())
        coEvery { healthConnectClient.aggregate(any<AggregateRequest>()) } throws IllegalStateException("boom")

        sensorManager.requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(any()) }
    }

    /**
     * Enables only the active calories sensor, with the Health Connect read permission granted, and captures what
     * is persisted when the sensor is updated.
     */
    private fun givenActiveCaloriesSensorEnabled(
        sensorSlot: CapturingSlot<Sensor>,
        attributesSlot: CapturingSlot<List<Attribute>>,
    ) {
        val sensorId = HealthConnectSensorManager.activeCaloriesBurned.id
        every {
            healthConnectClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        } returns HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns
            setOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
        coEvery { sensorRepository.get(sensorId) } returns
            listOf(Sensor(id = sensorId, serverId = 0, enabled = true, state = ""))
        coEvery { sensorRepository.update(capture(sensorSlot)) } returns Unit
        coEvery { sensorRepository.replaceAllAttributes(sensorId, capture(attributesSlot)) } returns Unit
    }

    private fun aggregationResult(energy: Energy?, origins: Set<String>) = mockk<AggregationResult> {
        every { get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) } returns energy
        every { dataOrigins } returns origins.map { DataOrigin(it) }.toSet()
    }
}
