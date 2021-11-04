package io.homeassistant.companion.android.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.UrlHandler

class NfcSetupActivity : BaseActivity() {

    private val viewModel: NfcViewModel by viewModels()
    private var mNfcAdapter: NfcAdapter? = null
    private var simpleWrite = false
    private var messageId: Int = -1

    companion object {
        val TAG = NfcSetupActivity::class.simpleName
        const val EXTRA_TAG_VALUE = "tag_value"
        const val EXTRA_MESSAGE_ID = "message_id"

        fun newInstance(context: Context, tagId: String? = null, messageId: Int = -1): Intent {
            return Intent(context, NfcSetupActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
                if (tagId != null)
                    putExtra(EXTRA_TAG_VALUE, tagId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_setup)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)

        intent.getStringExtra(EXTRA_TAG_VALUE)?.let {
            simpleWrite = true
            viewModel.nfcWriteTagEvent.postValue(it)
        }

        messageId = intent.getIntExtra(EXTRA_MESSAGE_ID, -1)
    }

    override fun onResume() {
        super.onResume()
        mNfcAdapter?.let {
            NFCUtil.enableNFCInForeground(it, this, javaClass)
        }
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.let {
            NFCUtil.disableNFCInForeground(it, this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val nfcTagToWriteUUID = viewModel.nfcWriteTagEvent.value

            // Create new nfc tag
            if (nfcTagToWriteUUID == null) {
                val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                val ndefMessage = rawMessages?.firstOrNull() as NdefMessage?
                val url = ndefMessage?.records?.get(0)?.toUri().toString()
                val nfcTagId = UrlHandler.splitNfcTagId(url)
                if (nfcTagId == null) {
                    Log.w(TAG, "Unable to read tag!")
                    Toast.makeText(this, R.string.nfc_invalid_tag, Toast.LENGTH_LONG).show()
                } else {
                    viewModel.nfcReadEvent.postValue(nfcTagId)
                }
            } else {
                try {
                    val nfcTagUrl = "https://www.home-assistant.io/tag/$nfcTagToWriteUUID"
                    NFCUtil.createNFCMessage(nfcTagUrl, intent)
                    Log.d(TAG, "Wrote nfc tag with url: $nfcTagUrl")
                    val message = R.string.nfc_write_tag_success
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

                    viewModel.nfcReadEvent.value = nfcTagToWriteUUID
                    viewModel.nfcWriteTagDoneEvent.value = nfcTagToWriteUUID
                    // If we are a simple write it means the fontend asked us to write.  This means
                    // we should return the user as fast as possible back to the UI to continue what
                    // they were doing!
                    if (simpleWrite) {
                        setResult(messageId)
                        finish()
                    }
                } catch (e: Exception) {
                    val message = R.string.nfc_write_tag_error
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Unable to write tag.", e)
                }
            }
        }
    }
}
