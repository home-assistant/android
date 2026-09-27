package io.homeassistant.companion.android.location

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.util.CHANNEL_SINGLE_ACCURATE_LOCATION
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SingleAccurateLocationServiceTest {

    private var controller: ServiceController<SingleAccurateLocationService>? = null

    @After
    fun tearDown() {
        controller?.destroy()
    }

    private fun createService(): SingleAccurateLocationService = Robolectric.buildService(SingleAccurateLocationService::class.java).create().also { controller = it }.get()

    @Test
    fun `Given service created when inspected then it runs in the foreground on its own channel`() {
        val service = createService()

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals(CHANNEL_SINGLE_ACCURATE_LOCATION, notification.channelId)
        val notificationManager = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            .getSystemService(NotificationManager::class.java)
        assertNotNull(notificationManager.getNotificationChannel(CHANNEL_SINGLE_ACCURATE_LOCATION))
    }

    @Test
    fun `Given Android refuses a foreground service when starting then report it was not started`() {
        val context = mockk<Context>(relaxed = true)
        every { context.startForegroundService(any()) } throws IllegalStateException("not allowed")

        assertFalse(SingleAccurateLocationService.start(context))
    }

    @Test
    fun `Given two requests running when the first one stops then the service keeps running until the second stops`() {
        val context = mockk<Context>(relaxed = true)
        SingleAccurateLocationService.start(context)
        SingleAccurateLocationService.start(context)
        verify(exactly = 1) { context.startForegroundService(any()) }
        createService()

        SingleAccurateLocationService.stop(context)
        verify(exactly = 0) { context.stopService(any()) }

        SingleAccurateLocationService.stop(context)
        verify(exactly = 1) { context.stopService(any()) }
    }

    @Test
    fun `Given stop requested before the service was created when created then it stops itself`() {
        SingleAccurateLocationService.start(ApplicationProvider.getApplicationContext())
        SingleAccurateLocationService.stop(ApplicationProvider.getApplicationContext())

        val service = createService()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `Given service running when maximum duration elapses then it stops itself`() {
        val service = createService()
        assertFalse(shadowOf(service).isStoppedBySelf)

        ShadowLooper.idleMainLooper(SingleAccurateLocationService.MAX_DURATION.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `Given service running when stopped before maximum duration then no self stop is pending`() {
        val service = createService()
        controller?.destroy()
        controller = null

        ShadowLooper.idleMainLooper(SingleAccurateLocationService.MAX_DURATION.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        assertFalse(shadowOf(service).isStoppedBySelf)
    }
}
