package io.homeassistant.companion.android.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScreenOffAdminRequestActivityTest {

    @Test
    fun `Given the activity is started when created then the device admin activation screen is opened`() {
        ActivityScenario.launch(ScreenOffAdminRequestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivityForResult

                assertNotNull(started)
                assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, started.intent.action)
                assertEquals(
                    ComponentName(activity, ScreenOffAdminReceiver::class.java),
                    @Suppress("DEPRECATION")
                    started.intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN),
                )
                assertNotNull(started.intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION))
            }
        }
    }

    @Test
    fun `Given a saved state when the activity is recreated then it finishes without a second activation request`() {
        val activity = Robolectric.buildActivity(ScreenOffAdminRequestActivity::class.java).setup(Bundle()).get()

        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertTrue(activity.isFinishing)
    }
}
