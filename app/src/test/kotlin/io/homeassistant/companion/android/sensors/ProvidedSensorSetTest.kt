package io.homeassistant.companion.android.sensors

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.SensorManager
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ProvidedSensorSetTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var sensors: Set<@JvmSuppressWildcards SensorManager.BasicSensor>

    @Test
    fun `Given hilt graph then provided sensor set is non-empty with unique ids and includes module sensors`() {
        hilt.inject()
        assertTrue(sensors.isNotEmpty())
        val ids = sensors.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("last_update"))
        assertTrue(ids.contains("car_fuel"))
        assertTrue(ids.contains("car_speed"))
    }
}
