package io.homeassistant.companion.android.sensors

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
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

    @Test
    fun `Given hilt graph then repository is injected with the generated sensor catalog`() {
        hilt.inject()
        assertTrue(repository.basicSensors.isNotEmpty())
        assertTrue(repository.basicSensors.any { it.id == "last_update" })
    }
}
