package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.WebChromeClient
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class FileChooserManagerTest {

    @Test
    fun `Given pickFiles called when result delivered then suspend returns the uris and slot clears`() = runTest {
        val manager = FileChooserManager()
        val params = mockk<WebChromeClient.FileChooserParams>(relaxed = true)

        val outcome = async { manager.pickFiles(params) }
        advanceUntilIdle()

        val pending = manager.pendingFileChooser.value
        assertNotNull(pending)
        assertEquals(params, pending!!.fileChooserParams)
        assertFalse(outcome.isCompleted)

        val uris = arrayOf(mockk<Uri>())
        pending.onResult(uris)
        advanceUntilIdle()

        assertEquals(uris, outcome.await())
        assertNull(manager.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles called when user cancels then suspend returns null and slot clears`() = runTest {
        val manager = FileChooserManager()

        val outcome = async { manager.pickFiles(mockk(relaxed = true)) }
        advanceUntilIdle()
        manager.pendingFileChooser.value!!.onResult(null)
        advanceUntilIdle()

        assertNull(outcome.await())
        assertNull(manager.pendingFileChooser.value)
    }

    @Test
    fun `Given chooser already pending when second pickFiles then it suspends until first completes`() = runTest {
        val manager = FileChooserManager()

        val first = async { manager.pickFiles(mockk(relaxed = true)) }
        advanceUntilIdle()
        val second = async { manager.pickFiles(mockk(relaxed = true)) }
        advanceUntilIdle()

        // Only the first request is exposed; the second is queued behind it.
        assertNotNull(manager.pendingFileChooser.value)
        assertFalse(second.isCompleted)

        manager.pendingFileChooser.value!!.onResult(null)
        advanceUntilIdle()
        assertTrue(first.isCompleted)
        // Second now holds the slot.
        assertNotNull(manager.pendingFileChooser.value)

        manager.pendingFileChooser.value!!.onResult(null)
        advanceUntilIdle()
        assertTrue(second.isCompleted)
        assertNull(manager.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles in flight when scope cancels then slot is cleared`() = runTest {
        val manager = FileChooserManager()

        val outcome = async { manager.pickFiles(mockk(relaxed = true)) }
        advanceUntilIdle()
        assertNotNull(manager.pendingFileChooser.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(manager.pendingFileChooser.value)
    }
}
