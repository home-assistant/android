package io.homeassistant.companion.android.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.UrlHandler

class NfcSetupActivity : AppCompatActivity() {

    val TAG = NfcSetupActivity::class.simpleName

    // private val viewModel: NfcViewModel by viewModels()
    private lateinit var viewModel: NfcViewModel
    private var mNfcAdapter: NfcAdapter? = null

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, NfcSetupActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_setup)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        viewModel = ViewModelProvider(this).get(NfcViewModel::class.java)
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
                    viewModel.postNewUUID()
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
                } catch (e: Exception) {
                    val message = R.string.nfc_write_tag_error
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Unable to write tag.", e)
                }
            }
        }
    }
}
