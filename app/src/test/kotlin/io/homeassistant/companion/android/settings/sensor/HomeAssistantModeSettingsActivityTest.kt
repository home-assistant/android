package io.homeassistant.companion.android.settings.sensor

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.sensors.HomeAssistantMode
import io.homeassistant.companion.android.settings.SettingsActivity
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val MODE_ID = "mode-id"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HomeAssistantModeSettingsActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val homeAssistantMode: HomeAssistantMode = mockk()

    private val modeLaunch = Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE)
        .putExtra(NotificationManager.EXTRA_AUTOMATIC_RULE_ID, MODE_ID)

    private fun launch(): HomeAssistantModeSettingsActivity {
        val activity = Robolectric.buildActivity(HomeAssistantModeSettingsActivity::class.java, modeLaunch)
            .setup()
            .get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `Given a launch for the app's mode when created then the sensor settings open and the activity finishes`() {
        coEvery { homeAssistantMode.isOwnModeLaunch(any()) } returns true

        val activity = launch()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started?.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Given a launch for another mode when created then nothing opens and the activity finishes`() {
        coEvery { homeAssistantMode.isOwnModeLaunch(any()) } returns false

        val activity = launch()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }
}
