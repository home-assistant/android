package io.homeassistant.companion.android.developer.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Bundle
import io.homeassistant.companion.android.nfc.NFCUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

internal fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

/**
 * The current content of the emulated tag.
 *
 * @property summary Human readable description of the NDEF records.
 * @property rawHex The raw bytes of the NDEF file (`NLEN` followed by the NDEF message).
 */
data class EmulatedTagContent(val summary: String, val rawHex: String)

/**
 * In-memory content of the emulated NFC tag, shared between [DebugNfcTagEmulatorService] (driven
 * by the OS when a reader selects the NDEF applet) and the Dev Playground UI.
 *
 * The emulated NDEF file is `NLEN` (2 bytes) followed by the NDEF message, per the NFC Forum
 * Type 4 Tag specification.
 */
object DebugNfcTagEmulatorState {

    /** Maximum size of the emulated NDEF file, advertised in the capability container. */
    const val MAX_NDEF_FILE_SIZE = 1024

    private val ndefFile = ByteArray(MAX_NDEF_FILE_SIZE)

    private val _content = MutableStateFlow(EmulatedTagContent(summary = "<empty>", rawHex = "0000"))

    /** The current tag content, for the Dev Playground. */
    val content: StateFlow<EmulatedTagContent> = _content.asStateFlow()

    /** Replaces the tag content with what the app writes to a real tag for [tagId]. */
    @Synchronized
    fun setTagId(tagId: String) {
        val message = NFCUtil.createTagMessage(NFCUtil.createTagUrl(tagId))
        val bytes = message.toByteArray()
        if (bytes.size > MAX_NDEF_FILE_SIZE - 2) {
            Timber.w("Tag message of ${bytes.size} bytes exceeds the emulated file size")
            ndefFile.fill(0)
            _content.value = EmulatedTagContent(summary = "<message too large: ${bytes.size} bytes>", rawHex = "0000")
            return
        }
        ndefFile.fill(0)
        ndefFile[0] = (bytes.size shr 8).toByte()
        ndefFile[1] = bytes.size.toByte()
        bytes.copyInto(ndefFile, destinationOffset = 2)
        updateSummary()
    }

    @Synchronized
    fun read(offset: Int, length: Int): ByteArray =
        ndefFile.copyOfRange(offset.coerceIn(0, ndefFile.size), (offset + length).coerceIn(0, ndefFile.size))

    @Synchronized
    fun write(offset: Int, data: ByteArray) {
        data.copyInto(ndefFile, destinationOffset = offset)
        updateSummary()
    }

    private fun updateSummary() {
        val messageLength = ((ndefFile[0].toInt() and 0xFF) shl 8) or (ndefFile[1].toInt() and 0xFF)
        val summary = if (messageLength == 0) {
            "<empty>"
        } else {
            try {
                NdefMessage(ndefFile.copyOfRange(2, 2 + messageLength))
                    .records
                    .joinToString { record -> record.describe() }
            } catch (e: Exception) {
                "<invalid NDEF: ${e.message}>"
            }
        }
        val rawEnd = (2 + messageLength).coerceAtMost(ndefFile.size)
        _content.value = EmulatedTagContent(summary = summary, rawHex = ndefFile.copyOfRange(0, rawEnd).toHex())
    }

    /**
     * Decodes URI and Android application records into readable text, like the app writes. The
     * application record check must come first: [NdefRecord.toUri] also matches it but renders
     * it as an opaque `vnd.android.nfc://ext/` URI without the application id.
     */
    private fun NdefRecord.describe(): String = when {
        tnf == NdefRecord.TNF_EXTERNAL_TYPE && type.contentEquals(ANDROID_PACKAGE_RECORD_TYPE) ->
            "app:${payload.decodeToString()}"
        toUri() != null -> toUri().toString()
        else -> toString()
    }
}

private val ANDROID_PACKAGE_RECORD_TYPE = "android.com:pkg".toByteArray()

/**
 * Emulates a writable NFC Forum Type 4 Tag through host card emulation, so another device can read
 * and write a Home Assistant NFC tag against this device without physical tag hardware.
 *
 * The emulated content is exposed and configurable in the Dev Playground through
 * [DebugNfcTagEmulatorState]. Every APDU is logged for debugging.
 */
class DebugNfcTagEmulatorService : android.nfc.cardemulation.HostApduService() {

    private var selectedFileId: Int? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = handle(commandApdu)
        Timber.d("APDU ${commandApdu.toHex()} -> ${response.toHex()}")
        return response
    }

    private fun handle(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return STATUS_WRONG_LENGTH

        val instruction = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)

        return when (instruction) {
            INS_SELECT if p1 == SELECT_BY_NAME -> selectApplication(apdu)
            INS_SELECT -> selectFile(apdu)
            INS_READ_BINARY -> readBinary(offset, length = apdu.getOrNull(4)?.toInt()?.and(0xFF) ?: 0)
            INS_UPDATE_BINARY -> updateBinary(offset, apdu)
            else -> STATUS_INS_NOT_SUPPORTED
        }
    }

    private fun selectApplication(apdu: ByteArray): ByteArray {
        val length = apdu.getOrNull(4)?.toInt()?.and(0xFF) ?: return STATUS_WRONG_LENGTH
        val aid = apdu.copyOfRange(5, (5 + length).coerceAtMost(apdu.size))
        return if (aid.contentEquals(NDEF_AID)) {
            selectedFileId = null
            STATUS_OK
        } else {
            STATUS_FILE_NOT_FOUND
        }
    }

    private fun selectFile(apdu: ByteArray): ByteArray {
        if (apdu.size < 7) return STATUS_WRONG_LENGTH
        val fileId = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
        return if (fileId == CC_FILE_ID || fileId == NDEF_FILE_ID) {
            selectedFileId = fileId
            STATUS_OK
        } else {
            STATUS_FILE_NOT_FOUND
        }
    }

    private fun readBinary(offset: Int, length: Int): ByteArray {
        val requested = if (length == 0) MAX_APDU_DATA_SIZE else length
        val data = when (selectedFileId) {
            CC_FILE_ID -> CC_FILE.copyOfRange(
                offset.coerceIn(0, CC_FILE.size),
                (offset + requested).coerceIn(0, CC_FILE.size),
            )
            NDEF_FILE_ID -> DebugNfcTagEmulatorState.read(offset, requested)
            else -> return STATUS_FILE_NOT_FOUND
        }
        return data + STATUS_OK
    }

    private fun updateBinary(offset: Int, apdu: ByteArray): ByteArray {
        if (selectedFileId != NDEF_FILE_ID) return STATUS_FILE_NOT_FOUND
        val length = apdu.getOrNull(4)?.toInt()?.and(0xFF) ?: return STATUS_WRONG_LENGTH
        if (apdu.size < 5 + length || offset + length > DebugNfcTagEmulatorState.MAX_NDEF_FILE_SIZE) {
            return STATUS_WRONG_LENGTH
        }
        DebugNfcTagEmulatorState.write(offset, apdu.copyOfRange(5, 5 + length))
        return STATUS_OK
    }

    override fun onDeactivated(reason: Int) {
        Timber.d("Deactivated: $reason")
        selectedFileId = null
    }

    companion object {
        /** NFC Forum Type 4 Tag application identifier. */
        private val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)

        private const val INS_SELECT = 0xA4
        private const val INS_READ_BINARY = 0xB0
        private const val INS_UPDATE_BINARY = 0xD6
        private const val SELECT_BY_NAME = 0x04

        private const val CC_FILE_ID = 0xE103
        private const val NDEF_FILE_ID = 0xE104
        private const val MAX_APDU_DATA_SIZE = 0xFF

        private val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val STATUS_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        private val STATUS_WRONG_LENGTH = byteArrayOf(0x67, 0x00)

        /**
         * Capability container: version 2.0, MLe/MLc 255 bytes, one NDEF file (id E104) of
         * [DebugNfcTagEmulatorState.MAX_NDEF_FILE_SIZE] bytes, freely readable and writable.
         */
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F, // CCLEN
            0x20, // mapping version 2.0
            0x00, 0xFF.toByte(), // MLe
            0x00, 0xFF.toByte(), // MLc
            0x04, 0x06, // NDEF file control TLV
            0xE1.toByte(), 0x04, // file id
            (DebugNfcTagEmulatorState.MAX_NDEF_FILE_SIZE shr 8).toByte(),
            DebugNfcTagEmulatorState.MAX_NDEF_FILE_SIZE.toByte(),
            0x00, // read access without security
            0x00, // write access without security
        )
    }
}
