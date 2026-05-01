package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class HealthConnectSensorManagerTest {

    private val sensorManager = HealthConnectSensorManager()

    private val context = mockk<Context> {
        every { applicationContext } returns this
    }
    private val healthConnectClient = mockk<HealthConnectClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(HealthConnectClient.Companion)
        every { HealthConnectClient.getOrCreate(any()) } returns healthConnectClient
        HealthConnectSensorManager.allowWritesCache.clear()
    }

    @AfterEach
    fun tearDown() {
        HealthConnectSensorManager.allowWritesCache.clear()
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
        val permissions = sensorManager.requiredPermissions(context, HealthConnectSensorManager.steps.id)
        assertEquals(
            available,
            permissions.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND),
        )
    }

    @Test
    fun `Write permission excluded when allow-writes toggle is off`() {
        every {
            healthConnectClient.features.getFeatureStatus(any())
        } returns HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE

        val perms = sensorManager.requiredPermissions(context, HealthConnectSensorManager.weight.id)

        assertFalse(perms.contains(HealthPermission.getWritePermission(WeightRecord::class)))
    }

    @Test
    fun `Write permission included once allow-writes toggle is enabled in cache`() {
        every {
            healthConnectClient.features.getFeatureStatus(any())
        } returns HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
        HealthConnectSensorManager.allowWritesCache[HealthConnectSensorManager.weight.id] = true

        val perms = sensorManager.requiredPermissions(context, HealthConnectSensorManager.weight.id)

        assertTrue(perms.contains(HealthPermission.getReadPermission(WeightRecord::class)))
        assertTrue(perms.contains(HealthPermission.getWritePermission(WeightRecord::class)))
    }

    @Test
    fun `Allow-writes toggle is per-sensor (does not leak to others)`() {
        every {
            healthConnectClient.features.getFeatureStatus(any())
        } returns HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
        HealthConnectSensorManager.allowWritesCache[HealthConnectSensorManager.weight.id] = true

        val stepsPerms = sensorManager.requiredPermissions(context, HealthConnectSensorManager.steps.id)

        assertFalse(stepsPerms.any { it.startsWith("android.permission.health.WRITE_") })
    }
}
