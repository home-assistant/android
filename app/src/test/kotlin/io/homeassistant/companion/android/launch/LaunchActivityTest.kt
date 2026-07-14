package io.homeassistant.companion.android.launch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Rational
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorWorker
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.di.ServerManagerModule
import io.homeassistant.companion.android.sensors.SensorReceiver
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerManagerModule::class)
@HiltAndroidTest
class LaunchActivityTest {

    @get:Rule
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

    @Test
    fun `Given PIP feature available and readiness reported when user leaves then PIP is entered`() {
        setPipFeatureAvailable(available = true)

        ActivityScenario.launch(LaunchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                pipViewModelOf(activity).onPipReadinessChanged(
                    PipReadiness(aspectRatio = Rational(16, 9)),
                )

                invokeOnUserLeaveHint(activity)

                assertTrue(activity.isInPictureInPictureMode)
            }
        }
    }

    @Test
    fun `Given PIP feature available but no readiness when user leaves then PIP is not entered`() {
        setPipFeatureAvailable(available = true)

        ActivityScenario.launch(LaunchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invokeOnUserLeaveHint(activity)

                assertFalse(activity.isInPictureInPictureMode)
            }
        }
    }

    @Test
    fun `Given device without PIP feature when user leaves then PIP is not entered`() {
        setPipFeatureAvailable(available = false)

        ActivityScenario.launch(LaunchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                pipViewModelOf(activity).onPipReadinessChanged(
                    PipReadiness(aspectRatio = Rational(16, 9)),
                )

                invokeOnUserLeaveHint(activity)

                assertFalse(activity.isInPictureInPictureMode)
            }
        }
    }

    @Test
    fun `Given showWhenLocked is true when launched then activity is shown over the lock screen`() {
        val intent = LaunchActivity.newInstance(ApplicationProvider.getApplicationContext(), showWhenLocked = true)

        assertEquals(Intent.ACTION_MAIN, intent.action)

        ActivityScenario.launch<LaunchActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(shadowOf(activity).showWhenLocked)
            }
        }
    }

    @Test
    fun `Given showWhenLocked is false when launched then activity is not shown over the lock screen`() {
        val intent = LaunchActivity.newInstance(ApplicationProvider.getApplicationContext(), showWhenLocked = false)

        assertEquals(Intent.ACTION_MAIN, intent.action)

        ActivityScenario.launch<LaunchActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(shadowOf(activity).showWhenLocked)
            }
        }
    }

    @Test
    fun `Given intent targets LaunchActivity directly with legacy extra then activity is not shown over the lock screen`() {
        // Models a hostile or stale caller that targets the exported LaunchActivity component
        // directly and tries to opt into the lock-screen behavior via the legacy extra. The
        // gating now lives on the non-exported alias, so direct-component intents must never
        // flip the window flag — regardless of any extra they carry.
        val intent = Intent(ApplicationProvider.getApplicationContext(), LaunchActivity::class.java).apply {
            putExtra("show_when_locked", true)
        }

        ActivityScenario.launch<LaunchActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(shadowOf(activity).showWhenLocked)
            }
        }
    }

    private fun setPipFeatureAvailable(available: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, available)
    }

    private fun pipViewModelOf(activity: LaunchActivity): LaunchViewModel = ViewModelProvider(activity)[LaunchViewModel::class.java]

    // `Activity.onUserLeaveHint` is `protected`, so reflect into Activity to dispatch through
    // the override. ActivityScenario does not expose a "user leaving" lifecycle helper.
    private fun invokeOnUserLeaveHint(activity: Activity) {
        val method = Activity::class.java.getDeclaredMethod("onUserLeaveHint")
        method.isAccessible = true
        method.invoke(activity)
    }
}
