package io.homeassistant.companion.android.thread

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ThreadManagerImplTest {

    @Test
    fun `Given minimal flavor when getting device dataset then fail with unsupported Thread error`() {
        val manager = ThreadManagerImpl()

        val exception = assertThrows(IllegalStateException::class.java) {
            runTest {
                manager.getPreferredDatasetFromDevice()
            }
        }

        assertEquals("Thread is not supported with the minimal flavor", exception.message)
    }
}
