package io.homeassistant.companion.android.util

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ScreenOffAdminReceiverTest {

    @Test
    fun `Given the screen turned off when the device admin is deactivated then the screen is turned back on`() {
        val screenOffHelper = mockk<ScreenOffHelper>(relaxed = true)
        val receiver = ScreenOffAdminReceiver().apply { this.screenOffHelper = screenOffHelper }

        receiver.onDisabled(mockk<Context>(), mockk<Intent>())

        verify(exactly = 1) { screenOffHelper.turnScreenOn() }
    }
}
