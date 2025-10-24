package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import timber.log.Timber

class HealthConnectSensorManagerTest {

    private val sensorManager = HealthConnectSensorManager()

    private val context = mockk<Context> {
        every { applicationContext } returns this
    }
    private val healthConnectClient = mockk<HealthConnectClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        Timber.plant(ConsoleLogTree)
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
        val permissions = sensorManager.requiredPermissions(context, HealthConnectSensorManager.steps.id)
        assertEquals(
            available,
            permissions.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND),
        )
    }
}
