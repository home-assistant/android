package io.homeassistant.companion.android.sensors

import android.location.Location
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTime::class)
class GeocodeSensorManagerTest {

    @Test
    fun `Given geocoded location sensor when inspected then its settings are declared`() {
        assertEquals(
            listOf(
                SensorManager.BasicSensor.Setting(
                    name = "geocode_minimum_accuracy",
                    type = SensorSettingType.NUMBER,
                    defaultValue = "200",
                ),
                SensorManager.BasicSensor.Setting(
                    name = GeocodeSensorManager.SETTINGS_INCLUDE_LOCATION,
                    type = SensorSettingType.TOGGLE,
                    defaultValue = "false",
                ),
            ),
            GeocodeSensorManager.geocodedLocation.settings,
        )
    }

    @Test
    fun `Given location time bellow threshold when invoking isStillValid then it returns true`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTime = now - 3.minutes
        every { location.time } returns validTime.toEpochMilliseconds()

        assertTrue(location.isStillValid())
    }

    @Test
    fun `Given location time bellow threshold with the 1024 weeks bug when invoking isStillValid then it returns true`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTimeWithBug = now - (1024 * 7).days - 2.minutes
        every { location.time } returns validTimeWithBug.toEpochMilliseconds()

        assertTrue(location.isStillValid())
    }

    @Test
    fun `Given location time above threshold when invoking isStillValid then it returns false`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTime = now - 14.days
        every { location.time } returns validTime.toEpochMilliseconds()

        assertFalse(location.isStillValid())
    }

    @Test
    fun `Given location time above threshold with the 1024 weeks bug when invoking isStillValid then it returns false`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTimeWithBug = now - (1024 * 7).days - 6.minutes
        every { location.time } returns validTimeWithBug.toEpochMilliseconds()

        assertFalse(location.isStillValid())
    }

    @Test
    fun `Given location time in the future when invoking isStillValid then it returns true`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTime = now + 5.seconds
        every { location.time } returns validTime.toEpochMilliseconds()

        assertTrue(location.isStillValid())
    }

    @Test
    fun `Given location time in the future below inaccuracy factor with the 1024 weeks bug when invoking isStillValid then it returns true`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTimeWithBug = now - (1024 * 7).days + 10.seconds
        every { location.time } returns validTimeWithBug.toEpochMilliseconds()

        assertTrue(location.isStillValid())
    }

    @Test
    fun `Given location time in the future above inaccuracy factor with the 1024 weeks bug when invoking isStillValid then it returns false`() {
        val now = Clock.System.now()
        val location = mockk<Location>()
        val validTimeWithBug = now - (1024 * 7).days + 11.seconds
        every { location.time } returns validTimeWithBug.toEpochMilliseconds()

        assertFalse(location.isStillValid())
    }
}
