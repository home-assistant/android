package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

        val result = sensorManager.calculateSleepDurationInMinutes(mockSleepStages)
        assertEquals(300L, result)
    }

    @Test
    fun `Given nutrition sensors when getting permission then requests nutrition records`() {
        val nutritionSensors = listOf(
            HealthConnectSensorManager.nutritionCalories,
            HealthConnectSensorManager.nutritionCarbohydrates,
            HealthConnectSensorManager.nutritionFat,
            HealthConnectSensorManager.nutritionProtein,
        )

        nutritionSensors.forEach { sensor ->
            val permissions = sensorManager.requiredPermissions(sensor.id)
            assertTrue(permissions.contains(HealthPermission.getReadPermission(NutritionRecord::class)))
        }
    }

    @Test
    fun `Given nutrition aggregate when requesting update then updates all nutrition sensors`() = runTest {
        val nutritionPermission = HealthPermission.getReadPermission(NutritionRecord::class)
        coEvery { healthConnectClient.permissionController.getGrantedPermissions() } returns setOf(nutritionPermission)
        mockkObject(healthConnectClient.features)
        every {
            healthConnectClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        } returns HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE

        val expectedStates = mapOf(
            HealthConnectSensorManager.nutritionCalories.id to "420.13",
            HealthConnectSensorManager.nutritionProtein.id to "21.13",
            HealthConnectSensorManager.nutritionCarbohydrates.id to "58.13",
            HealthConnectSensorManager.nutritionFat.id to "12.13",
        )
        expectedStates.keys.forEach { sensorId ->
            coEvery { sensorRepository.get(sensorId) } returns listOf(
                Sensor(id = sensorId, serverId = 0, enabled = true, state = ""),
            )
        }

        val aggregateResult = mockk<AggregationResult>()
        every { aggregateResult[NutritionRecord.ENERGY_TOTAL] } returns Energy.kilocalories(420.126)
        every { aggregateResult[NutritionRecord.PROTEIN_TOTAL] } returns Mass.grams(21.126)
        every { aggregateResult[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL] } returns Mass.grams(58.126)
        every { aggregateResult[NutritionRecord.TOTAL_FAT_TOTAL] } returns Mass.grams(12.126)
        every { aggregateResult.dataOrigins } returns emptySet()
        coEvery { healthConnectClient.aggregate(any()) } returns aggregateResult

        val updatedStates = mutableMapOf<String, String>()
        val updatedSensor = slot<Sensor>()
        coEvery { sensorRepository.update(capture(updatedSensor)) } answers {
            updatedStates[updatedSensor.captured.id] = updatedSensor.captured.state
        }

        sensorManager.requestSensorUpdate()

        assertEquals(expectedStates, updatedStates)
        coVerify(exactly = expectedStates.size) { sensorRepository.update(any()) }
    }
}
