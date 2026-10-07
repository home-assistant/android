package io.homeassistant.companion.android.common.util

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextExtTest {

    private val launcher = mockk<ActivityResultLauncher<String>>(relaxed = true)

    @Test
    fun `Given launch succeeds when launching catching then true is returned`() {
        assertTrue(launcher.launchCatching("input"))

        verify { launcher.launch("input") }
    }

    @Test
    fun `Given no activity to handle the input when launching catching then false is returned`() {
        every { launcher.launch(any()) } throws ActivityNotFoundException()

        assertFalse(launcher.launchCatching("input"))
    }

    @Test
    fun `Given launching is not allowed when launching catching then false is returned`() {
        every { launcher.launch(any()) } throws SecurityException()

        assertFalse(launcher.launchCatching("input"))
    }
}
