package io.homeassistant.companion.android.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.nfc.views.LoadNfcView
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class NfcSetupActivity : BaseActivity() {

    private val viewModel: NfcViewModel by viewModels()
    private var mNfcAdapter: NfcAdapter? = null
    private var simpleWrite = false
    private var messageId: Int = -1

    private val nfcStateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) viewModel.checkNfcEnabled()
        }
    }

    companion object {
        val TAG = NfcSetupActivity::class.simpleName
        const val EXTRA_TAG_VALUE = "tag_value"
        const val EXTRA_MESSAGE_ID = "message_id"

        const val NAV_WELCOME = "nfc_welcome"
        const val NAV_READ = "nfc_read"
        const val NAV_WRITE = "nfc_write"
        const val NAV_EDIT = "nfc_edit"

        fun newInstance(context: Context, tagId: String? = null, messageId: Int = -1): Intent {
            return Intent(context, NfcSetupActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
                if (tagId != null) {
                    putExtra(EXTRA_TAG_VALUE, tagId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.getStringExtra(EXTRA_TAG_VALUE)?.let {
            simpleWrite = true
            viewModel.writeNewTagSimple(it)
        }

        setContent {
            MdcTheme {
                LoadNfcView(
                    viewModel = viewModel,
                    startDestination = if (simpleWrite) NAV_WRITE else NAV_WELCOME,
                    pressedUpAtRoot = { finish() }
                )
            }
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)

        messageId = intent.getIntExtra(EXTRA_MESSAGE_ID, -1)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNfcEnabled()
        mNfcAdapter?.let {
            NFCUtil.enableNFCInForeground(it, this, javaClass)
        }
        registerReceiver(
            nfcStateChangedReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        )
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.let {
            NFCUtil.disableNFCInForeground(it, this)
        }
        unregisterReceiver(nfcStateChangedReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            lifecycleScope.launch {
                val nfcTagToWriteUUID = viewModel.nfcTagIdentifier

                // Create new nfc tag
                if (!viewModel.nfcEventShouldWrite) {
                    val url = NFCUtil.extractUrlFromNFCIntent(intent)
                    val nfcTagId = UrlUtil.splitNfcTagId(url)
                    if (nfcTagId == null) {
                        viewModel.onNfcReadEmpty()
                    } else {
                        viewModel.onNfcReadSuccess(nfcTagId)
                    }
                } else {
                    try {
                        val nfcTagUrl = "https://www.home-assistant.io/tag/$nfcTagToWriteUUID"
                        NFCUtil.createNFCMessage(nfcTagUrl, intent)
                        Log.d(TAG, "Wrote nfc tag with url: $nfcTagUrl")

                        // If we are a simple write it means the frontend asked us to write. This means
                        // we should return the user as fast as possible back to the UI to continue what
                        // they were doing!
                        if (simpleWrite) {
                            val message = commonR.string.nfc_write_tag_success
                            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

                            setResult(messageId)
                            finish()
                        } else {
                            viewModel.onNfcWriteSuccess(nfcTagToWriteUUID!!)
                        }
                    } catch (e: Exception) {
                        viewModel.onNfcWriteFailure()
                        Log.e(TAG, "Unable to write tag.", e)
                    }
                }
            }
        }
    }
}
