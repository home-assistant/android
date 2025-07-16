package io.homeassistant.companion.android.common.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class FailFastTest {
    private var exceptionCaught: Throwable? = null

    @BeforeEach
    fun setUp() {
        FailFast.setHandler(object : FailFastHandler {
            override fun handleException(throwable: Throwable, additionalMessage: String?) {
                exceptionCaught = throwable
            }
        })
    }

    @AfterEach
    fun tearDown() {
        exceptionCaught = null
    }

    @Test
    fun `Given a throw when invoking failOnCatch then properly propagate the error with custom stacktrace`() {
        FailFast.failOnCatch {
            throw IllegalArgumentException("expected error")
        }

        assertEquals("java.lang.IllegalArgumentException: expected error", exceptionCaught?.message)
        assertEquals(FailFastTest::class.java.name, exceptionCaught?.stackTrace?.first()?.className)
    }

    @Test
    fun `Given a throw when invoking failOnCatch then properly propagate the error and returns fallback value`() {
        val returnedValue = FailFast.failOnCatch<Int>(fallback = 42) {
            throw IllegalArgumentException("expected error")
        }
        assertEquals("java.lang.IllegalArgumentException: expected error", exceptionCaught?.message)
        assertEquals(42, returnedValue)
    }

    @Test
    fun `Given no throw when invoking failOnCatch then returns value`() {
        val returnedValue = FailFast.failOnCatch<Int>(fallback = 0) {
            42
        }
        assertNull(exceptionCaught)
        assertEquals(42, returnedValue)
    }

    @Test
    fun `Given condition not met when invoking failWhen then returns value`() {
        FailFast.failWhen(false) { "fail" }
        assertNull(exceptionCaught)
    }

    @Test
    fun `Given condition met when invoking failWhen then properly propagate the error`() {
        FailFast.failWhen(true) { "expected error" }
        assertEquals("expected error", exceptionCaught?.message)
    }
}
