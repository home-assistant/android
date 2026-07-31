package io.homeassistant.companion.android.developer.nfc

import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.BuildConfig
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG_ID = "test-tag"
private val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)
private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())

private val SELECT_NDEF_APPLICATION =
    byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) +
        byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
private val SELECT_CC_FILE = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
private val SELECT_NDEF_FILE = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DebugNfcTagEmulatorServiceTest {

    private val service = DebugNfcTagEmulatorService()

    @Before
    fun setup() {
        DebugNfcTagEmulatorState.setTagId(TAG_ID)
    }

    private fun readBinary(offset: Int, length: Int) = service.processCommandApdu(
        byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), length.toByte()),
        null,
    )

    private fun updateBinary(offset: Int, data: ByteArray) = service.processCommandApdu(
        byteArrayOf(0x00, 0xD6.toByte(), (offset shr 8).toByte(), offset.toByte(), data.size.toByte()) + data,
        null,
    )

    @Test
    fun `Given NDEF application selected when reading capability container then it describes a writable NDEF file`() {
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_APPLICATION, null))
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_CC_FILE, null))

        val response = readBinary(offset = 0, length = 15)

        // CCLEN, version 2.0
        assertEquals(0x00, response[0].toInt())
        assertEquals(0x0F, response[1].toInt())
        assertEquals(0x20, response[2].toInt())
        // Freely readable and writable
        assertEquals(0x00, response[13].toInt())
        assertEquals(0x00, response[14].toInt())
        assertArrayEquals(STATUS_OK, response.copyOfRange(15, 17))
    }

    @Test
    fun `Given NDEF file selected when reading then content contains the tag url`() {
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_APPLICATION, null))
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_FILE, null))

        val response = readBinary(offset = 0, length = 100)

        val content = response.dropLast(2).toByteArray().decodeToString()
        assertTrue(content.contains("home-assistant.io/tag/$TAG_ID"))
    }

    @Test
    fun `Given NDEF file selected when writing then the new content is readable and reflected in the summary`() {
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_APPLICATION, null))
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_FILE, null))

        // Write a new NDEF message for another tag id like a real writer would: content then length
        DebugNfcTagEmulatorState.setTagId("placeholder")
        val newContent = DebugNfcTagEmulatorState.read(0, DebugNfcTagEmulatorState.MAX_NDEF_FILE_SIZE)
        DebugNfcTagEmulatorState.setTagId(TAG_ID)

        val messageLength = ((newContent[0].toInt() and 0xFF) shl 8) or (newContent[1].toInt() and 0xFF)
        assertArrayEquals(STATUS_OK, updateBinary(offset = 2, data = newContent.copyOfRange(2, 2 + messageLength)))
        assertArrayEquals(STATUS_OK, updateBinary(offset = 0, data = newContent.copyOfRange(0, 2)))

        assertTrue(DebugNfcTagEmulatorState.content.value.summary.contains("placeholder"))
        assertTrue(
            DebugNfcTagEmulatorState.content.value.rawHex.startsWith(newContent.copyOfRange(0, 2).toHex()),
        )
    }

    @Test
    fun `Given a tag id when set then the summary lists the tag url and the application ids`() {
        val summary = DebugNfcTagEmulatorState.content.value.summary

        assertTrue(summary.contains("home-assistant.io/tag/$TAG_ID"))
        BuildConfig.APPLICATION_IDS.forEach { applicationId ->
            assertTrue(summary.contains(applicationId))
        }
    }

    @Test
    fun `Given unknown file when selecting then file not found is returned`() {
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_APPLICATION, null))

        val response = service.processCommandApdu(byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0x12, 0x34), null)

        assertArrayEquals(STATUS_FILE_NOT_FOUND, response)
    }

    @Test
    fun `Given no file selected when reading then file not found is returned`() {
        assertArrayEquals(STATUS_OK, service.processCommandApdu(SELECT_NDEF_APPLICATION, null))

        assertArrayEquals(STATUS_FILE_NOT_FOUND, readBinary(offset = 0, length = 10))
    }
}
