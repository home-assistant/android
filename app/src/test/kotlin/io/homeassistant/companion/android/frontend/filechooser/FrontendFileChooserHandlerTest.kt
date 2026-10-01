package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendFileChooserHandlerTest {

    private val outputUri = mockk<Uri>()
    private val cameraCaptureRepository = mockk<CameraCaptureRepository>(relaxed = true) {
        every { hasCamera } returns true
        coEvery { createImageFile() } returns outputUri
    }
    private val permissionManager = mockk<PermissionManager> {
        coEvery { checkCameraPermission() } returns true
    }
    private val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager)

    @Test
    fun `Given pickFiles called when result delivered then suspend returns the uris and slot clears`() = runTest {
        val params = FakeFileChooserParams()

        val outcome = async { handler.pickFiles(params) }
        val pending = awaitPick()
        assertEquals(params, pending.input.params)
        assertFalse(outcome.isCompleted)

        val uris = listOf(mockk<Uri>(), mockk<Uri>())
        pending.onResult(FileChooserResult.Selected(uris))
        advanceUntilIdle()

        assertArrayEquals(uris.toTypedArray(), outcome.await())
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles called when user cancels then suspend returns null and slot clears`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        awaitPick().onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()

        assertNull(outcome.await())
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given chooser already pending when second pickFiles then it suspends until first completes`() = runTest {
        val first = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()
        val second = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()

        // Only the first request is exposed; the second is queued behind it.
        assertNotNull(handler.pendingFileChooser.value)
        assertFalse(second.isCompleted)

        awaitPick().onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()
        assertTrue(first.isCompleted)
        // Second now holds the slot.
        assertNotNull(handler.pendingFileChooser.value)

        awaitPick().onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()
        assertTrue(second.isCompleted)
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles in flight when scope cancels then slot is cleared`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()
        assertNotNull(handler.pendingFileChooser.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given camera offered when scope cancels then the capture file is deleted`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"))) }
        awaitPick()

        outcome.cancel()
        advanceUntilIdle()

        coVerify(exactly = 1) { cameraCaptureRepository.delete(outputUri) }
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles called then previous captures are deleted before a new one is created`() = runTest {
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"))) }
        awaitPick()

        coVerifyOrder {
            cameraCaptureRepository.deleteAll()
            cameraCaptureRepository.createImageFile()
        }
    }

    @Test
    fun `Given no image accepted when picking then camera is not involved`() = runTest {
        listOf(arrayOf(""), arrayOf("*/*"), arrayOf("application/pdf"), arrayOf("image/*", "*/*")).forEach { acceptTypes ->
            async { handler.pickFiles(FakeFileChooserParams(acceptTypes = acceptTypes, captureEnabled = true)) }

            val pending = awaitPick()
            assertNull(pending.input.cameraCapture, "Camera involved for ${acceptTypes.toList()}")
            pending.onResult(FileChooserResult.Cancelled)
            advanceUntilIdle()
        }
        coVerify(exactly = 0) { cameraCaptureRepository.createImageFile() }
    }

    @Test
    fun `Given only images accepted without capture when picking then camera is offered`() = runTest {
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/png", "image/jpeg", "image/gif")))
        }

        assertEquals(CameraCapture.Offered(outputUri), awaitPick().input.cameraCapture)
    }

    @Test
    fun `Given capture with only images accepted when picking then camera opens directly`() = runTest {
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/png", "image/jpeg"), captureEnabled = true)) }

        assertEquals(CameraCapture.Direct(outputUri), awaitPick().input.cameraCapture)
    }

    @Test
    fun `Given capture with images and other types accepted when picking then camera is offered`() = runTest {
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "application/pdf"), captureEnabled = true)) }

        assertEquals(CameraCapture.Offered(outputUri), awaitPick().input.cameraCapture)
    }

    @Test
    fun `Given device without camera when picking images then camera is not involved`() = runTest {
        every { cameraCaptureRepository.hasCamera } returns false

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick().input.cameraCapture)
    }

    @Test
    fun `Given capture file creation fails when picking images then only the file picker is shown`() = runTest {
        coEvery { cameraCaptureRepository.createImageFile() } throws IOException("disk full")

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick().input.cameraCapture)
    }

    @Test
    fun `Given camera permission denied when picking images then only the file picker is shown`() = runTest {
        coEvery { permissionManager.checkCameraPermission() } returns false

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick().input.cameraCapture)
        coVerify(exactly = 0) { cameraCaptureRepository.createImageFile() }
    }

    @Test
    fun `Given no image accepted when picking then camera permission is not checked`() = runTest {
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("application/pdf"), captureEnabled = true))
        }
        awaitPick()

        coVerify(exactly = 0) { permissionManager.checkCameraPermission() }
    }

    @Test
    fun `Given camera used when photo captured then the capture uri is returned and kept`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
        awaitPick().onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertArrayEquals(arrayOf(outputUri), outcome.await())
        coVerify(exactly = 0) { cameraCaptureRepository.delete(any()) }
    }

    @Test
    fun `Given camera used when camera returns the capture uri then it is returned and kept`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
        awaitPick().onResult(FileChooserResult.Selected(listOf(outputUri)))
        advanceUntilIdle()

        assertArrayEquals(arrayOf(outputUri), outcome.await())
        coVerify(exactly = 0) { cameraCaptureRepository.delete(any()) }
    }

    @Test
    fun `Given camera used when user picks a file or cancels then the capture file is deleted`() = runTest {
        listOf(FileChooserResult.Selected(listOf(mockk())), FileChooserResult.Cancelled).forEach { result ->
            async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
            awaitPick().onResult(result)
            advanceUntilIdle()
        }

        coVerify(exactly = 2) { cameraCaptureRepository.delete(outputUri) }
    }

    @Test
    fun `Given camera not involved when result has no uri then null is returned`() = runTest {
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        awaitPick().onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertNull(outcome.await())
    }

    /** Also runs [TestScope.backgroundScope] work, which [advanceUntilIdle] ignores. */
    private fun TestScope.awaitPick(): FileChooserRequest {
        advanceUntilIdle()
        runCurrent()
        val pending = handler.pendingFileChooser.value
        assertNotNull(pending)
        return pending
    }
}
