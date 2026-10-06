package io.homeassistant.companion.android.common.util

import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.ConsoleLogPlatformListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val OVERRIDDEN_SDK_INT = 1

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PlatformTestSupportRobolectricTest {
    @Test
    fun `1 Given Robolectric test execution when overriding fail-fast handler and SDK level then current test sees overrides`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        var handlerInvoked = false
        FailFast.setHandler { _, _ -> handlerInvoked = true }
        SdkVersion.sdkInt = OVERRIDDEN_SDK_INT

        FailFast.fail { "custom handler should handle this failure" }

        assertTrue(handlerInvoked)
        assertEquals(OVERRIDDEN_SDK_INT, SdkVersion.sdkInt)
    }

    @Test
    fun `2 Given previous test changed state when next Robolectric test starts then plugin resets fail-fast handler and SDK level`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        val failure = assertThrows(AssertionError::class.java) {
            FailFast.fail { "plugin-managed failure" }
        }
        assertTrue(failure.message.orEmpty().contains("Unhandled FailFast exception caught during test"))
        assertNotNull(failure.cause)
        assertEquals(RuntimeEnvironment.getApiLevel(), SdkVersion.sdkInt)
    }
}
