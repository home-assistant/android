package io.homeassistant.companion.android.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FailFastTest {

    @Test
    fun `Given a throw when invoking failOnCatch then properly propagate the error with custom stacktrace`() {
        var exceptionCaught: Exception? = null
        FailFast.setHandler(object : FailFastHandler {
            override fun handleException(exception: Exception, additionalMessage: String?) {
                exceptionCaught = exception
            }
        })
        FailFast.failOnCatch {
            throw IllegalArgumentException("expected error")
        }

        assertEquals(exceptionCaught?.message, "java.lang.IllegalArgumentException: expected error")
        assertEquals(FailFastTest::class.java.name, exceptionCaught?.stackTrace?.first()?.className)
    }
}
