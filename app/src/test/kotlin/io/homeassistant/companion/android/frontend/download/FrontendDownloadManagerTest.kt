package io.homeassistant.companion.android.frontend.download

import android.app.DownloadManager
import android.net.Uri
import android.webkit.URLUtil
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.EvaluateScriptUsage
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.util.DataUriDownloadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class, EvaluateScriptUsage::class)
class FrontendDownloadManagerTest {
    private val systemDownloadManager: DownloadManager = mockk(relaxed = true)
    private val dataUriDownloadManager: DataUriDownloadManager = mockk(relaxed = true)
    private val sessionManager: ServerSessionManager = mockk(relaxed = true)
    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)

    private lateinit var manager: FrontendDownloadManager

    @BeforeEach
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uriString = firstArg<String>()
            createMockUri(uriString)
        }

        mockkStatic(URLUtil::class)
        every { URLUtil.guessFileName(any(), any(), any()) } returns "downloaded_file"

        mockkStatic(JSONObject::class)
        every { JSONObject.quote(any()) } answers { "\"${firstArg<String>()}\"" }

        mockkConstructor(DownloadManager.Request::class)
        every { anyConstructed<DownloadManager.Request>().setMimeType(any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().setNotificationVisibility(any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().setDestinationInExternalPublicDir(any(), any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().addRequestHeader(any(), any()) } answers { self as DownloadManager.Request }

        coEvery { externalBusRepository.evaluateScript(any()) } returns null

        manager = FrontendDownloadManager(
            systemDownloadManager = systemDownloadManager,
            dataUriDownloadManager = dataUriDownloadManager,
            sessionManager = sessionManager,
            externalBusRepository = externalBusRepository,
        )
    }

    private fun createMockUri(uriString: String): Uri {
        val scheme = try {
            URL(uriString).protocol
        } catch (_: Exception) {
            uriString.substringBefore(":").takeIf { it != uriString }
        }
        return mockk<Uri> {
            every { this@mockk.scheme } returns scheme
            every { this@mockk.toString() } returns uriString
        }
    }

    @Nested
    inner class BlobDownload {

        @Test
        fun `Given blob URL when downloadFile called then evaluates blob trigger script and returns Forwarded`() = runTest {
            val scriptSlot = slot<String>()
            coEvery { externalBusRepository.evaluateScript(capture(scriptSlot)) } returns null

            val result = manager.downloadFile(
                url = "blob:https://example.com/abc-123",
                contentDisposition = "",
                mimetype = "application/pdf",
                serverId = 1,
            )

            assertEquals(DownloadResult.Forwarded, result)
            coVerify { externalBusRepository.evaluateScript(any()) }
            assertTrue(scriptSlot.captured.contains("type:'handleBlob',data:reader.result"))
            assertTrue(scriptSlot.captured.contains("fetch("))
        }
    }

    @Nested
    inner class HttpDownload {

        @Test
        fun `Given https URL with safe credentials when downloadFile called then enqueues download with auth and returns Forwarded`() = runTest {
            coEvery { sessionManager.canSafelySendCredentials(serverId = 1, url = "https://example.com/file.pdf") } returns true
            coEvery { sessionManager.getAuthorizationHeader(serverId = 1) } returns "Bearer token123"

            val result = manager.downloadFile(
                url = "https://example.com/file.pdf",
                contentDisposition = "attachment; filename=\"file.pdf\"",
                mimetype = "application/pdf",
                serverId = 1,
            )

            assertEquals(DownloadResult.Forwarded, result)
            coVerify { systemDownloadManager.enqueue(any()) }
            verify { anyConstructed<DownloadManager.Request>().addRequestHeader("Authorization", "Bearer token123") }
        }

        @Test
        fun `Given https URL without safe credentials when downloadFile called then enqueues download without auth and returns Forwarded`() = runTest {
            coEvery { sessionManager.canSafelySendCredentials(serverId = 1, url = "https://untrusted.com/file.pdf") } returns false

            val result = manager.downloadFile(
                url = "https://untrusted.com/file.pdf",
                contentDisposition = "attachment; filename=\"file.pdf\"",
                mimetype = "application/pdf",
                serverId = 1,
            )

            assertEquals(DownloadResult.Forwarded, result)
            coVerify { systemDownloadManager.enqueue(any()) }
            verify(exactly = 0) {
                anyConstructed<DownloadManager.Request>().addRequestHeader("Authorization", any())
            }
        }

        @Test
        fun `Given null system download manager when downloadFile called with https URL then returns Error`() = runTest {
            val managerWithoutSystem = FrontendDownloadManager(
                systemDownloadManager = null,
                dataUriDownloadManager = dataUriDownloadManager,
                sessionManager = sessionManager,
                externalBusRepository = externalBusRepository,
            )

            val result = managerWithoutSystem.downloadFile(
                url = "https://example.com/file.pdf",
                contentDisposition = "attachment; filename=\"file.pdf\"",
                mimetype = "application/pdf",
                serverId = 1,
            )

            assertTrue(result is DownloadResult.Error)
            assertEquals(commonR.string.downloads_failed, (result as DownloadResult.Error).messageResId)
        }
    }

    @Nested
    inner class DataUriDownload {

        @Test
        fun `Given data URL when downloadFile called then saves via DataUriDownloadManager and returns Forwarded`() = runTest {
            val dataUrl = "data:application/pdf;base64,SGVsbG8="

            val result = manager.downloadFile(
                url = dataUrl,
                contentDisposition = "",
                mimetype = "application/pdf",
                serverId = 1,
            )

            assertEquals(DownloadResult.Forwarded, result)
            coVerify { dataUriDownloadManager.saveDataUri(url = dataUrl, mimetype = "application/pdf") }
        }
    }

    @Nested
    inner class UnknownScheme {

        @Test
        fun `Given unknown scheme URL when downloadFile called then returns OpenWithSystem`() = runTest {
            val result = manager.downloadFile(
                url = "ftp://example.com/file.txt",
                contentDisposition = "",
                mimetype = "text/plain",
                serverId = 1,
            )

            assertTrue(result is DownloadResult.OpenWithSystem)
            assertEquals("ftp://example.com/file.txt", (result as DownloadResult.OpenWithSystem).uri.toString())
        }
    }

    @Nested
    inner class HandleBlob {

        @Test
        fun `Given blob data when handleBlob called then saves via DataUriDownloadManager and returns Forwarded`() = runTest {
            val data = "data:application/pdf;base64,SGVsbG8="
            val filename = "test.pdf"

            val result = manager.handleBlob(data = data, filename = filename)

            assertEquals(DownloadResult.Forwarded, result)
            coVerify {
                dataUriDownloadManager.saveDataUri(
                    url = data,
                    mimetype = "",
                    filename = filename,
                )
            }
        }
    }
}
