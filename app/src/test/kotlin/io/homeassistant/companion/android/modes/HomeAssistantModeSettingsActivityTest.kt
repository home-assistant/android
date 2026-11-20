package io.homeassistant.companion.android.modes

import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.IntentCompat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.settings.SettingsActivity
import io.mockk.every
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

private const val SETTINGS_DEEPLINK_EXTRA = "fragment"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HomeAssistantModeSettingsActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val homeAssistantMode: HomeAssistantMode = mockk()

    private fun launch(): HomeAssistantModeSettingsActivity = Robolectric.buildActivity(
        HomeAssistantModeSettingsActivity::class.java,
        Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE),
    ).setup().get()

    @Test
    fun `Given a device with the mode when launched from the mode's page then the mode setting opens and the activity finishes`() {
        every { homeAssistantMode.isSupported() } returns true

        val activity = launch()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started?.component?.className)
        assertEquals(
            SettingsActivity.Deeplink.HomeAssistantMode,
            IntentCompat.getParcelableExtra(started, SETTINGS_DEEPLINK_EXTRA, SettingsActivity.Deeplink::class.java),
        )
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `Given a device without the mode when launched then nothing opens and the activity finishes`() {
        every { homeAssistantMode.isSupported() } returns false

        val activity = launch()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }
}
