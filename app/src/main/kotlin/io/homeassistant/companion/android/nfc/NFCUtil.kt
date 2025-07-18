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

    @Throws(Exception::class)
    fun createNFCMessage(url: String, intent: Intent?): Boolean {
        val nfcRecord = NdefRecord.createUri(url)
        val applicationRecords = BuildConfig.APPLICATION_IDS.map {
            NdefRecord.createApplicationRecord(it)
        }

        val nfcMessage = NdefMessage(arrayOf(nfcRecord) + applicationRecords)
        val nfcFallbackMessage = NdefMessage(arrayOf(nfcRecord))
        intent?.let {
            val tag = IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java)
            return writeMessageToTag(nfcMessage, nfcFallbackMessage, tag)
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

    @Throws(Exception::class)
    private fun writeMessageToTag(nfcMessage: NdefMessage, fallbackMessage: NdefMessage, tag: Tag?): Boolean {
        val nDefTag = Ndef.get(tag)

        nDefTag?.let {
            it.connect()
            var messageToWrite = nfcMessage
            if (it.maxSize < nfcMessage.toByteArray().size) {
                messageToWrite = fallbackMessage
            }
            if (it.maxSize < fallbackMessage.toByteArray().size) {
                // Message to large to write to NFC tag
                throw Exception("Message is too large")
            }
            return if (it.isWritable) {
                it.writeNdefMessage(messageToWrite)
                it.close()
                // Message is written to tag
                true
            } else {
                throw Exception("NFC tag is read-only")
            }
        }

        val nDefFormatableTag = NdefFormatable.get(tag)

        nDefFormatableTag?.let {
            try {
                it.connect()
                it.format(nfcMessage)
                it.close()
                // The data is written to the tag
            } catch (e: IOException) {
                // Failed to format tag
                throw Exception("Failed to format tag", e)
            }
        }
        return true
    }
}
