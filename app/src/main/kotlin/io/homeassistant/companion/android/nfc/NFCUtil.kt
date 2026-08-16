package io.homeassistant.companion.android.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.core.content.IntentCompat
import io.homeassistant.companion.android.BuildConfig
import java.io.IOException

object NFCUtil {
    fun extractUrlFromNFCIntent(intent: Intent): Uri? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED && intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) {
            return null
        }

        val rawMessages = IntentCompat.getParcelableArrayExtra(
            intent,
            NfcAdapter.EXTRA_NDEF_MESSAGES,
            NdefMessage::class.java,
        )
        val ndefMessage = rawMessages?.get(0) as NdefMessage?
        return ndefMessage?.records?.get(0)?.toUri()
    }

    /**
     * Write the given [url] to the tag in [intent].
     *
     * @param url URL to write to the tag.
     * @param intent Tag to write to, in an intent which holds the extra [NfcAdapter.EXTRA_TAG].
     *
     * @throws IllegalArgumentException if the url doesn't fit on the tag.
     * @throws IOException if the tag is NDEF and the url would fit, but it cannot be written to.
     * @throws Exception for other tag operation exceptions.
     *
     * @return `true` if the tag was successfully written to, `false` if the tag doesn't support NDEF messages,
     * throws if writing wasn't possible.
     */
    @Throws(IllegalArgumentException::class, IOException::class, Exception::class)
    fun createNFCMessage(url: String, intent: Intent?): Boolean {
        val nfcRecord = NdefRecord.createUri(url)
        val applicationFlavorsRecords = BuildConfig.APPLICATION_IDS.map {
            NdefRecord.createApplicationRecord(it)
        }
        val thisApplicationRecord = NdefRecord.createApplicationRecord(BuildConfig.APPLICATION_ID)

        val nfcMessages = listOf(
            NdefMessage(arrayOf(nfcRecord) + applicationFlavorsRecords),
            NdefMessage(arrayOf(nfcRecord, thisApplicationRecord)),
            NdefMessage(arrayOf(nfcRecord)),
        )
        intent?.let {
            val tag = IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java)
            return writeMessageToTag(nfcMessages, tag)
        }
        return false
    }

    fun disableNFCInForeground(nfcAdapter: NfcAdapter, activity: Activity) {
        nfcAdapter.disableForegroundDispatch(activity)
    }

    fun <T> enableNFCInForeground(nfcAdapter: NfcAdapter, activity: Activity, classType: Class<T>) {
        val pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            Intent(activity, classType).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE,
        )
        val nfcIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val filters = arrayOf(nfcIntentFilter)

        val techLists =
            arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name))
        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    /**
     * Write a message to an NFC tag. The first message in [nfcMessages] that fits on the given [tag] will
     * be written to the tag.
     *
     * @param nfcMessages List of available messages to write to a NFC tag. The first message that fits (size) will be
     * written, later messages should be smaller and serve as fallback messages.
     * @param tag The NFC tag to write the message to.
     *
     * @throws IllegalArgumentException if none of the messages fit on the tag.
     * @throws IOException if the tag is NDEF and a message would fit, but it cannot be written to.
     * @throws Exception for other tag operation exceptions.
     *
     * @return `true` if the tag was successfully written to, `false` if the tag doesn't support NDEF messages,
     * throws if writing wasn't possible.
     */
    @Throws(IllegalArgumentException::class, IOException::class, Exception::class)
    private fun writeMessageToTag(nfcMessages: List<NdefMessage>, tag: Tag?): Boolean {
        val nDefTag = Ndef.get(tag)
        nDefTag?.use {
            it.connect()
            val messageToWrite = nfcMessages.firstOrNull { message ->
                message.toByteArray().size <= it.maxSize
            } ?: throw IllegalArgumentException("Message is too large")
            return if (it.isWritable) {
                it.writeNdefMessage(messageToWrite)
                // Message is written to tag
                true
            } else {
                throw IOException("NFC tag is read-only")
            }
        }

        val nDefFormatableTag = NdefFormatable.get(tag)
        nDefFormatableTag?.let {
            var caughtException: IOException? = null
            // Tag wasn't Ndef yet, so we don't know the size. Try all messages until the last one fails.
            for (message in nfcMessages) {
                it.use { formatableTag ->
                    try {
                        formatableTag.connect()
                        formatableTag.format(message)
                        // The data is written to the tag
                        return true
                    } catch (e: IOException) {
                        // Failed to format tag with message, try next
                        caughtException = IOException("Failed to format tag", e)
                    }
                }
            }
            caughtException?.let { e -> throw e }
        }

        // Not already Ndef or Ndef Formatable
        return false
    }
}
