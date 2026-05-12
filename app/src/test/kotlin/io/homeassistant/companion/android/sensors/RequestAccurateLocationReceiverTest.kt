package io.homeassistant.companion.android.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RequestAccurateLocationReceiverTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    @Test
    fun `Given accurate update action when receiving then forward explicit broadcast to LocationSensorManager without extra`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "io.homeassistant.companion.android"
        val forwarded = slot<Intent>()
        every { context.sendBroadcast(capture(forwarded)) } returns Unit

        val incoming = Intent(LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE).apply {
            putExtra("extra-extra", "payload")
        }
        RequestAccurateLocationReceiver().onReceive(context, incoming)

        verify(exactly = 1) { context.sendBroadcast(any()) }
        assertEquals(
            LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE,
            forwarded.captured.action,
        )
        assertEquals(
            ComponentName(context, LocationSensorManager::class.java),
            forwarded.captured.component,
        )
        assertEquals(null, forwarded.captured.extras)
    }

    @Test
    fun `Given unexpected action when receiving then do not forward anything`() {
        val context = mockk<Context>(relaxed = true)

        val incoming = Intent("io.homeassistant.companion.android.background.PROCESS_UPDATES")
        RequestAccurateLocationReceiver().onReceive(context, incoming)

        verify(exactly = 0) { context.sendBroadcast(any()) }
    }

    @Test
    fun `Given null action when receiving then do not forward anything`() {
        val context = mockk<Context>(relaxed = true)

        RequestAccurateLocationReceiver().onReceive(context, Intent())

        verify(exactly = 0) { context.sendBroadcast(any()) }
    }
}
