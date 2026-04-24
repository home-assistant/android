package io.homeassistant.companion.android.launch

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.di.ServerManagerModule
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerManagerModule::class)
@HiltAndroidTest
class LaunchActivityTest {

    @get:Rule(order = 0)
    val consoleLogRule = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val serverManager: ServerManager = mockk(relaxed = true) {
        coEvery { getServer(any<Int>()) } returns null
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
        mockkObject(SensorWorker.Companion)
        mockkObject(WebsocketManager.Companion)
        mockkObject(SensorReceiver.Companion)
        mockkObject(DisabledLocationHandler)
        every { SensorWorker.start(any()) } just Runs
        coEvery { WebsocketManager.start(any()) } just Runs
        every { SensorReceiver.updateAllSensors(any()) } just Runs
        every { DisabledLocationHandler.isLocationEnabled(any()) } returns true
        mockkConstructor(ChangeLog::class)
        coEvery { anyConstructed<ChangeLog>().showChangeLog(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(SensorWorker.Companion)
        unmockkObject(WebsocketManager.Companion)
        unmockkObject(SensorReceiver.Companion)
        unmockkObject(DisabledLocationHandler)
        unmockkConstructor(ChangeLog::class)
    }

    @Test
    fun `Given activity resumes then sensor worker and websocket manager are started and changelog is shown`() {
        ActivityScenario.launch(LaunchActivity::class.java).use {
            verify { SensorWorker.start(any()) }
            coVerify { WebsocketManager.start(any()) }
            verify { DisabledLocationHandler.isLocationEnabled(any()) }
            coVerify { anyConstructed<ChangeLog>().showChangeLog(any(), eq(false)) }
        }
    }

    @Test
    fun `Given activity pauses without finishing then all sensors are updated`() {
        ActivityScenario.launch(LaunchActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED) // triggers onPause
            verify { SensorReceiver.updateAllSensors(any()) }
        }
    }
}
