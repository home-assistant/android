package io.homeassistant.companion.android.sensors

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RequestAccurateLocationUpdateIntentTest {

    private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()

    @Test
    fun `Given a request not from the notification command when building the intent then it carries no extra`() {
        val intent = LocationSensorManager.createRequestAccurateLocationUpdateIntent(context)

        assertEquals(LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE, intent.action)
        assertEquals(ComponentName(context, LocationSensorReceiver::class.java), intent.component)
        assertNull(intent.extras)
    }

    @Test
    fun `Given a request from the notification command when building the intent then it is marked as such`() {
        val intent = LocationSensorManager.createRequestAccurateLocationUpdateIntent(
            context,
            fromNotificationCommand = true,
        )

        assertEquals(LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE, intent.action)
        assertEquals(setOf("from_notification_command"), intent.extras?.keySet())
        assertEquals(true, intent.extras?.getBoolean("from_notification_command"))
    }
}
