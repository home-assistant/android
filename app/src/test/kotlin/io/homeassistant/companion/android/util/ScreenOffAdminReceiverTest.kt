package io.homeassistant.companion.android.util

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScreenOffAdminReceiverTest {

    @Test
    fun `Given the screen turned off when the device admin is deactivated then the screen is turned back on`() {
        val screenOffHelper = mockk<ScreenOffHelper>(relaxed = true)
        val receiver = ScreenOffAdminReceiver().apply { this.screenOffHelper = screenOffHelper }

        receiver.onDisabled(ApplicationProvider.getApplicationContext(), Intent())

        verify(exactly = 1) { screenOffHelper.turnScreenOn() }
    }
}
