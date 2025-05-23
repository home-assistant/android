package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import io.homeassistant.companion.android.launch.LaunchActivity
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushManagerTest {
    @Test
    fun testProviders() {
        Robolectric.buildActivity(LaunchActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            val pushManager = activity.pushManager

            val expect: Map<Class<*>, PushProvider> = mapOf(
                FirebasePushProvider::class.java to mockk<FirebasePushProvider>()
            )

            assertNotNull(pushManager.providers)
            assertEquals(pushManager.providers.size, expect.size)
            expect.forEach { (key, value) ->
                assertTrue(pushManager.providers[key]?.let { it::class } == value::class)
            }
        }
    }
}
