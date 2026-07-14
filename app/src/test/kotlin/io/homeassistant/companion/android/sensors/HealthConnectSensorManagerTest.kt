package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HealthConnectSensorManagerTest {

    private val context = mockk<Context> {
        every { applicationContext } returns this
    }

    private val sensorManager = HealthConnectSensorManager(context, mockk(), mockk())
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
}
