package io.homeassistant.companion.android.frontend.filechooser

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendFileChooserHandlerTest {

    private val photoUri = mockk<Uri>()
    private val videoUri = mockk<Uri>()
    private val photoOutput = CaptureOutput(CaptureKind.Photo, photoUri)
    private val videoOutput = CaptureOutput(CaptureKind.Video, videoUri)
    private val cameraCaptureRepository = mockk<CameraCaptureRepository>(relaxed = true) {
        every { hasCamera } returns true
        coEvery { createFile(CaptureKind.Photo) } returns photoUri
        coEvery { createFile(CaptureKind.Video) } returns videoUri
        coEvery { hasContent(photoUri) } returns true
    }
    private val permissionManager = mockk<PermissionManager> {
        coEvery { checkCameraPermission() } returns true
    }

    // The MIME table is empty on the JVM, conversion itself is covered by ShowWebFileChooserTest.
    @BeforeEach
    fun setUp() {
        mockkStatic(MimeTypeMap::class)
        every { MimeTypeMap.getSingleton() } returns mockk {
            every { hasMimeType(any()) } returns true
            every { getMimeTypeFromExtension(any()) } returns null
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(MimeTypeMap::class)
    }

    @Test
    fun `Given pickFiles called when result delivered then suspend returns the uris and slot clears`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val params = FakeFileChooserParams()

        val outcome = async { handler.pickFiles(params) }
        val pending = awaitPick(handler)
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
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        awaitPick(handler).onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()

        assertNull(outcome.await())
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given chooser already pending when second pickFiles then it suspends until first completes`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val first = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()
        val second = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()

        // Only the first request is exposed; the second is queued behind it.
        assertNotNull(handler.pendingFileChooser.value)
        assertFalse(second.isCompleted)

        awaitPick(handler).onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()
        assertTrue(first.isCompleted)
        // Second now holds the slot.
        assertNotNull(handler.pendingFileChooser.value)

        awaitPick(handler).onResult(FileChooserResult.Cancelled)
        advanceUntilIdle()
        assertTrue(second.isCompleted)
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles in flight when scope cancels then slot is cleared`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        advanceUntilIdle()
        assertNotNull(handler.pendingFileChooser.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given camera offered when scope cancels then the capture file is deleted`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"))) }
        awaitPick(handler)

        outcome.cancel()
        advanceUntilIdle()

        coVerify(exactly = 1) { cameraCaptureRepository.delete(photoUri) }
        assertNull(handler.pendingFileChooser.value)
    }

    @Test
    fun `Given pickFiles called then stale captures are deleted before a new one is created`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"))) }
        awaitPick(handler)

        coVerifyOrder {
            cameraCaptureRepository.deleteStale()
            cameraCaptureRepository.createFile(CaptureKind.Photo)
        }
    }

    @Test
    fun `Given images accepted with a wildcard when picking then camera is offered`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "*/*"), captureEnabled = true))
        }

        assertEquals(CameraCapture.Offered(listOf(photoOutput)), awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given images or videos accepted when saving or picking a folder then camera is not involved`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        listOf(FileChooserParams.MODE_SAVE, FileChooserParams.MODE_OPEN_FOLDER).forEach { mode ->
            async {
                handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "video/*"), mode = mode, captureEnabled = true))
            }

            val pending = awaitPick(handler)
            assertNull(pending.input.cameraCapture, "Camera involved for mode $mode")
            pending.onResult(FileChooserResult.Cancelled)
            advanceUntilIdle()
        }
        coVerify(exactly = 0) { permissionManager.checkCameraPermission() }
    }

    @Test
    fun `Given no image accepted when picking then camera is not involved`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        listOf(arrayOf(""), arrayOf("*/*"), arrayOf("application/pdf")).forEach { acceptTypes ->
            async { handler.pickFiles(FakeFileChooserParams(acceptTypes = acceptTypes, captureEnabled = true)) }

            val pending = awaitPick(handler)
            assertNull(pending.input.cameraCapture, "Camera involved for ${acceptTypes.toList()}")
            pending.onResult(FileChooserResult.Cancelled)
            advanceUntilIdle()
        }
        coVerify(exactly = 0) { cameraCaptureRepository.createFile(any()) }
    }

    @Test
    fun `Given only images accepted without capture when picking then camera is offered`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/png", "image/jpeg", "image/gif")))
        }

        assertEquals(CameraCapture.Offered(listOf(photoOutput)), awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given capture with only images accepted when picking then camera opens directly`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/png", "image/jpeg"), captureEnabled = true)) }

        assertEquals(CameraCapture.Direct(photoOutput), awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given capture with images and other types accepted when picking then camera is offered`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "application/pdf"), captureEnabled = true)) }

        assertEquals(CameraCapture.Offered(listOf(photoOutput)), awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given device without camera when picking images then camera is not involved`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        every { cameraCaptureRepository.hasCamera } returns false

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given capture file creation fails when picking images then only the file picker is shown`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        coEvery { cameraCaptureRepository.createFile(any()) } throws IOException("disk full")

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given camera permission denied when picking images then only the file picker is shown`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        coEvery { permissionManager.checkCameraPermission() } returns false

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }

        assertNull(awaitPick(handler).input.cameraCapture)
        coVerify(exactly = 0) { cameraCaptureRepository.createFile(any()) }
    }

    @Test
    fun `Given no image accepted when picking then camera permission is not checked`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("application/pdf"), captureEnabled = true))
        }
        awaitPick(handler)

        coVerify(exactly = 0) { permissionManager.checkCameraPermission() }
    }

    @Test
    fun `Given camera used when photo captured then the capture uri is returned and kept`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
        awaitPick(handler).onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertArrayEquals(arrayOf(photoUri), outcome.await())
        coVerify(exactly = 0) { cameraCaptureRepository.delete(any()) }
    }

    @Test
    fun `Given camera used when camera returns the capture uri then it is returned and kept`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
        awaitPick(handler).onResult(FileChooserResult.Selected(listOf(photoUri)))
        advanceUntilIdle()

        assertArrayEquals(arrayOf(photoUri), outcome.await())
        coVerify(exactly = 0) { cameraCaptureRepository.delete(any()) }
    }

    @Test
    fun `Given camera used when user picks a file or cancels then the capture file is deleted`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        listOf(FileChooserResult.Selected(listOf(mockk())), FileChooserResult.Cancelled).forEach { result ->
            async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)) }
            awaitPick(handler).onResult(result)
            advanceUntilIdle()
        }

        coVerify(exactly = 2) { cameraCaptureRepository.delete(photoUri) }
    }

    @Test
    fun `Given videos accepted when picking then recording is offered or opened directly with capture`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        listOf(false to CameraCapture.Offered(listOf(videoOutput)), true to CameraCapture.Direct(videoOutput))
            .forEach { (captureEnabled, expected) ->
                async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("video/*"), captureEnabled = captureEnabled)) }

                val pending = awaitPick(handler)
                assertEquals(expected, pending.input.cameraCapture, "Unexpected capture with capture=$captureEnabled")
                pending.onResult(FileChooserResult.Cancelled)
                advanceUntilIdle()
            }
    }

    @Test
    fun `Given images and videos accepted with capture when picking then both are offered`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "video/*"), captureEnabled = true))
        }

        assertEquals(CameraCapture.Offered(listOf(photoOutput, videoOutput)), awaitPick(handler).input.cameraCapture)
    }

    @Test
    fun `Given photo and video offered when video recorded then the video is returned and the photo file deleted`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        coEvery { cameraCaptureRepository.hasContent(photoUri) } returns false
        coEvery { cameraCaptureRepository.hasContent(videoUri) } returns true

        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "video/*"))) }
        awaitPick(handler).onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertArrayEquals(arrayOf(videoUri), outcome.await())
        coVerify(exactly = 1) { cameraCaptureRepository.delete(photoUri) }
        coVerify(exactly = 0) { cameraCaptureRepository.delete(videoUri) }
    }

    @Test
    fun `Given camera used when completed without any content then null is returned and files deleted`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        coEvery { cameraCaptureRepository.hasContent(any()) } returns false

        val outcome = async { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "video/*"))) }
        awaitPick(handler).onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertNull(outcome.await())
        coVerify(exactly = 1) { cameraCaptureRepository.delete(photoUri) }
        coVerify(exactly = 1) { cameraCaptureRepository.delete(videoUri) }
    }

    @Test
    fun `Given video file creation fails after photo file when picking then photo file is deleted`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        coEvery { cameraCaptureRepository.createFile(CaptureKind.Video) } throws IOException("disk full")

        backgroundScope.launch { handler.pickFiles(FakeFileChooserParams(acceptTypes = arrayOf("image/*", "video/*"))) }

        assertNull(awaitPick(handler).input.cameraCapture)
        coVerify(exactly = 1) { cameraCaptureRepository.delete(photoUri) }
    }

    @Test
    fun `Given camera not involved when result has no uri then null is returned`() = runTest {
        val handler = FrontendFileChooserHandler(cameraCaptureRepository, permissionManager, StandardTestDispatcher(testScheduler))
        val outcome = async { handler.pickFiles(FakeFileChooserParams()) }
        awaitPick(handler).onResult(FileChooserResult.Captured)
        advanceUntilIdle()

        assertNull(outcome.await())
    }

    /** Also runs [TestScope.backgroundScope] work, which [advanceUntilIdle] ignores. */
    private fun TestScope.awaitPick(handler: FrontendFileChooserHandler): FileChooserRequest {
        advanceUntilIdle()
        runCurrent()
        val pending = handler.pendingFileChooser.value
        assertNotNull(pending)
        return pending
    }
}
