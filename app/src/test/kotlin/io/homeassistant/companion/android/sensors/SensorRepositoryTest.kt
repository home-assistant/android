package io.homeassistant.companion.android.sensors

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SensorRepositoryTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var repository: SensorRepository

    @Inject
    lateinit var basicSensors: Set<@JvmSuppressWildcards SensorManager.BasicSensor>

    @Test
    fun `Given hilt graph then repository is built from the generated sensor set`() {
        hilt.inject()
        assertNotNull(repository)
        // That set is the generated multibinding, populated with the annotated sensors.
        assertTrue(basicSensors.isNotEmpty())
        assertTrue(basicSensors.any { it.id == "last_update" })
    }
}
