package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.testing.unit.ConsoleLogPlatformListener
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PlatformTestSupportJUnit4Test {
    @Test
    fun `1 Given listener-managed test execution when overriding fail-fast handler then current test sees override`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        var handlerInvoked = false
        val customHandler = FailFastHandler { _, _ -> handlerInvoked = true }

        FailFast.setHandler(customHandler)
        FailFast.fail { "custom handler should handle this failure" }

        assertTrue(handlerInvoked)
    }

    @Test
    fun `2 Given previous test changed handler when next Jupiter test starts then listener installs test fail-fast handler`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        val failure = assertThrows(AssertionError::class.java) {
            FailFast.fail { "listener-managed failure" }
        }
        assertTrue(failure.message.orEmpty().contains("Unhandled FailFast exception caught during test"))
        assertNotNull(failure.cause)
    }
}
