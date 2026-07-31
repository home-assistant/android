package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.core.content.IntentCompat
import io.homeassistant.companion.android.BuildConfig
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.IOException
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TAG_URL = "https://www.home-assistant.io/tag/123e4567-e89b-12d3-a456-426614174000"

private const val TINY_TAG_SIZE_IN_BYTES = 32
private const val SMALL_TAG_SIZE_IN_BYTES = 100
private const val NTAG213_TAG_SIZE_IN_BYTES = 144
private const val NTAG215_TAG_SIZE_IN_BYTES = 504

/** List of test cases with tag size to expected number of records to be written */
private val TAG_CASES = listOf(
    SMALL_TAG_SIZE_IN_BYTES to 1, // URL only
    NTAG213_TAG_SIZE_IN_BYTES to 2, // URL and one app ID
    NTAG215_TAG_SIZE_IN_BYTES to (BuildConfig.APPLICATION_IDS.size + 1), // URL and all app IDs
)

/**
 * Tests for writing tags with NFCUtil.
 *
 * This test class uses Robolectric (JUnit 4) because anything NFC related and its use of [Uri]
 * are Android framework classes.
 */
@RunWith(RobolectricTestRunner::class)
class NFCUtilTest {

    private lateinit var tagDiscoveredIntent: Intent
    private lateinit var tag: Tag

    @Before
    fun setup() {
        tagDiscoveredIntent = mockk(relaxed = true)
        tag = mockk()

        mockkStatic(IntentCompat::class)
        every { IntentCompat.getParcelableExtra(tagDiscoveredIntent, any(), Tag::class.java) } returns tag
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given NDEF tag with sufficient size when writing then writes expected number of records`() {
        val messageSlot = slot<NdefMessage>()
        val ndefMock = mockNdef(messageSlot)

        // Loop through cases as JUnit 4 can only parametrize the entire class
        TAG_CASES.forEach { (tagSize, expectedNumberOfRecords) ->
            every { ndefMock.maxSize } returns tagSize
            messageSlot.clear()

            val success = NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent)

            assertEquals(true, success)
            val message = messageSlot.captured
            assertNotNull(message.records)
            assertEquals(expectedNumberOfRecords, message.records.size)
            assertEquals(TAG_URL, message.records[0].toUri().toString())
        }
    }

    @Test
    fun `Given NDEF tag with tiny size when writing then throws message too large exception`() {
        val messageSlot = slot<NdefMessage>()
        val ndefMock = mockNdef(messageSlot)
        every { ndefMock.maxSize } returns TINY_TAG_SIZE_IN_BYTES

        try {
            NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent)
            fail("Expected IllegalArgumentException to be thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("Message is too large", e.message)
        }
    }

    @Test
    fun `Given NDEF tag non-writable when writing then throws non-writable exception`() {
        val messageSlot = slot<NdefMessage>()
        val ndefMock = mockNdef(messageSlot, canWrite = false)
        every { ndefMock.maxSize } returns NTAG215_TAG_SIZE_IN_BYTES

        try {
            NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent)
            fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            assertEquals("NFC tag is read-only", e.message)
        }
    }

    @Test
    fun `Given NDEF formatable tag with sufficient size when writing then writes expected number of records`() {
        val ndefFormatableMock = mockNdefFormatable()

        // Loop through cases as JUnit 4 can only parametrize the entire class
        TAG_CASES.forEach { (tagSize, expectedNumberOfRecords) ->
            val messageSlot = slot<NdefMessage>()
            every { ndefFormatableMock.format(capture(messageSlot)) } answers {
                if (messageSlot.captured.toByteArray().size <= tagSize) {
                    Unit
                } else {
                    throw IOException()
                }
            }

            val success = NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent)

            assertEquals(true, success)
            val message = messageSlot.captured
            assertNotNull(message.records)
            assertEquals(expectedNumberOfRecords, message.records.size)
            assertEquals(TAG_URL, message.records[0].toUri().toString())
        }
    }

    @Test
    fun `Given NDEF formatable tag non-functional when writing then throws message too large exception`() {
        // Non-functional can mean: the message is too big, formatting exception, tag lost, ...
        // The specifics are inside the platforms API so we don't care about it here, but any failures
        // for NDEF formatable are expected to throw.
        val ndefFormatableMock = mockNdefFormatable()
        every { ndefFormatableMock.format(any()) } throws IOException()

        try {
            NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent)
            fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            assertEquals("Failed to format tag", e.message)
        }
    }

    @Test
    fun `Given tag without NDEF support when writing then returns no success`() {
        mockkStatic(Ndef::class)
        every { Ndef.get(tag) } returns null
        mockkStatic(NdefFormatable::class)
        every { NdefFormatable.get(tag) } returns null

        assertEquals(false, NFCUtil.createNFCMessage(TAG_URL, tagDiscoveredIntent))
    }

    private fun mockNdef(writeSlot: CapturingSlot<NdefMessage>, canWrite: Boolean = true): Ndef {
        val ndefMock = mockk<Ndef>(relaxed = true) {
            every { isWritable } returns canWrite
        }

        mockkStatic(Ndef::class)
        every { Ndef.get(tag) } returns ndefMock
        every { ndefMock.writeNdefMessage(capture(writeSlot)) } just Runs

        return ndefMock
    }

    private fun mockNdefFormatable(): NdefFormatable {
        // First mock Ndef, if it can be formatted that means it is not already Ndef
        mockkStatic(Ndef::class)
        every { Ndef.get(tag) } returns null

        // Then mock NdefFormatable
        val ndefFormatableMock = mockk<NdefFormatable>(relaxed = true)

        mockkStatic(NdefFormatable::class)
        every { NdefFormatable.get(tag) } returns ndefFormatableMock

        return ndefFormatableMock
    }
}
