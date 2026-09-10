package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.testing.unit.ConsoleLogPlatformListener
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertNotNull

@TestMethodOrder(OrderAnnotation::class)
class PlatformTestSupportJUnitJupiterTest {
    @Test
    @Order(1)
    fun `Given listener-managed test execution when overriding fail-fast handler then current test sees override`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        var handlerInvoked = false
        val customHandler = FailFastHandler { _, _ -> handlerInvoked = true }

        FailFast.setHandler(customHandler)
        FailFast.fail { "custom handler should handle this failure" }

        assertTrue(handlerInvoked)
    }

    @Test
    @Order(2)
    fun `Given previous test changed handler when next Jupiter test starts then listener installs test fail-fast handler`() {
        assertTrue(ConsoleLogPlatformListener.isConsoleLogEnabled())
        val failure = assertThrows(AssertionError::class.java) {
            FailFast.fail { "listener-managed failure" }
        }
        assertTrue(failure.message.orEmpty().contains("Unhandled FailFast exception caught during test"))
        assertNotNull(failure.cause)
    }
}
