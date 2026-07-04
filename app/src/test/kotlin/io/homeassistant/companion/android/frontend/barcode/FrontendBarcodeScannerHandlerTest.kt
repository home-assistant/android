package io.homeassistant.companion.android.frontend.barcode

import com.google.zxing.BarcodeFormat
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendBarcodeScannerHandlerTest {

    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val dialogManager = FrontendDialogManager()
    private val manager = FrontendBarcodeScannerHandler(externalBusRepository, dialogManager)

    @Test
    fun `Given show when called then state carries the scan request`() = runTest {
        manager.show(messageId = 7, title = "Scan", description = "Point camera", alternativeOptionLabel = "Manual")

        val state = manager.state.value
        assertEquals(7, state?.messageId)
        assertEquals("Scan", state?.title)
        assertEquals("Point camera", state?.description)
        assertEquals("Manual", state?.alternativeOptionLabel)
    }

    @Test
    fun `Given an active scan when show is called again then it replaces the state`() = runTest {
        manager.show(messageId = 1, title = "A", description = "a", alternativeOptionLabel = null)
        manager.show(messageId = 2, title = "B", description = "b", alternativeOptionLabel = null)

        assertEquals(2, manager.state.value?.messageId)
        assertEquals("B", manager.state.value?.title)
    }

    @Test
    fun `Given an active scan when notify then an information dialog is shown with the message`() = runTest {
        manager.show(messageId = 1, title = "A", description = "a", alternativeOptionLabel = null)
        // notify suspends until the dialog is dismissed, so collect it in the background.
        backgroundScope.launch { manager.notify("Already paired") }
        runCurrent()

        val dialog = assertInstanceOf(FrontendDialog.Information::class.java, dialogManager.pendingDialog.value)
        assertEquals("Already paired", dialog.message)
    }

    @Test
    fun `Given no active scan when notify then no dialog is shown`() = runTest {
        backgroundScope.launch { manager.notify("ignored") }
        runCurrent()

        assertNull(dialogManager.pendingDialog.value)
    }

    @Test
    fun `Given a notify dialog when dismissed then it clears but scan stays active`() = runTest {
        manager.show(messageId = 1, title = "A", description = "a", alternativeOptionLabel = null)
        backgroundScope.launch { manager.notify("msg") }
        runCurrent()

        val dialog = assertInstanceOf(FrontendDialog.Information::class.java, dialogManager.pendingDialog.value)
        dialog.onDismiss()
        runCurrent()

        assertNull(dialogManager.pendingDialog.value)
        assertEquals(1, manager.state.value?.messageId)
    }

    @Test
    fun `Given an active scan when close then state clears and nothing is sent`() = runTest {
        manager.show(messageId = 1, title = "A", description = "a", alternativeOptionLabel = null)
        manager.close()

        assertNull(manager.state.value)
        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given an active scan when onScanned then sends scan_result and keeps the scanner open`() = runTest {
        val sent = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(sent)) } returns Unit
        manager.show(messageId = 7, title = "A", description = "a", alternativeOptionLabel = null)

        manager.onScanned(rawValue = "HA-12345", format = BarcodeFormat.QR_CODE)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/scan_result","payload":{"rawValue":"HA-12345","format":"qr_code"}}""",
            frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(sent.captured),
        )
        // The scanner stays open until the frontend closes it (bar_code/close); a scan alone does not.
        assertEquals(7, manager.state.value?.messageId)
    }

    @Test
    fun `Given no active scan when onScanned then nothing is sent`() = runTest {
        manager.onScanned(rawValue = "x", format = BarcodeFormat.QR_CODE)

        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given an active scan when onCancelled forAction true then sends aborted with alternative_options`() = runTest {
        val sent = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(sent)) } returns Unit
        manager.show(messageId = 7, title = "A", description = "a", alternativeOptionLabel = null)

        manager.onCancelled(forAction = true)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/aborted","payload":{"reason":"alternative_options"}}""",
            frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(sent.captured),
        )
        assertNull(manager.state.value)
    }

    @Test
    fun `Given an active scan when onCancelled forAction false then sends aborted with canceled`() = runTest {
        val sent = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(sent)) } returns Unit
        manager.show(messageId = 7, title = "A", description = "a", alternativeOptionLabel = null)

        manager.onCancelled(forAction = false)

        assertEquals(
            """{"type":"command","id":7,"command":"bar_code/aborted","payload":{"reason":"canceled"}}""",
            frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(sent.captured),
        )
        assertNull(manager.state.value)
    }

    @Test
    fun `Given no active scan when onCancelled then nothing is sent`() = runTest {
        manager.onCancelled(forAction = false)

        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given a scanned format when onScanned then it maps to the frontend wire string`() = runTest {
        suspend fun assertFormat(format: BarcodeFormat, expected: String) {
            val sent = slot<OutgoingExternalBusMessage>()
            coEvery { externalBusRepository.send(capture(sent)) } returns Unit
            manager.show(messageId = 3, title = "A", description = "a", alternativeOptionLabel = null)

            manager.onScanned(rawValue = "v", format = format)

            assertEquals(
                """{"type":"command","id":3,"command":"bar_code/scan_result","payload":{"rawValue":"v","format":"$expected"}}""",
                frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(sent.captured),
            )
        }

        BarcodeFormat.entries.forEach {
            val expected = when (it) {
                BarcodeFormat.PDF_417 -> "pdf417"
                BarcodeFormat.MAXICODE,
                BarcodeFormat.RSS_14,
                BarcodeFormat.RSS_EXPANDED,
                BarcodeFormat.UPC_EAN_EXTENSION,
                -> "unknown"

                BarcodeFormat.AZTEC,
                BarcodeFormat.CODABAR,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                -> it.toString().lowercase(Locale.getDefault())
            }

            assertFormat(it, expected)
        }
    }
}
