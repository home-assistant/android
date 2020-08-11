package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.UrlHandler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TagReaderActivity : AppCompatActivity() {

    val TAG = TagReaderActivity::class.simpleName

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_reader)

        setSupportActionBar(findViewById(R.id.toolbar))

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        mainScope.launch {
            if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                val ndefMessage = rawMessages[0] as NdefMessage?
                val url = ndefMessage?.records?.get(0)?.toUri().toString()
                try {
                    handleTag(url)
                } catch (e: Exception) {
                    val message = R.string.nfc_processing_tag_error
                    Toast.makeText(this@TagReaderActivity, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Unable to handle url (nfc): $url", e)
                    finish()
                }
            } else if (Intent.ACTION_VIEW == intent.action) {
                val url: String = intent?.data.toString()
                try {
                    handleTag(url)
                } catch (e: Exception) {
                    val message = R.string.qrcode_processing_tag_error
                    Toast.makeText(this@TagReaderActivity, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Unable to handle url (qrcode): $url", e)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleTag(url: String) {
        // https://www.home-assistant.io/tag/5f0ba733-172f-430d-a7f8-e4ad940c88d7

        val nfcTagId = UrlHandler.splitNfcTagId(url)
        if (nfcTagId != null) {
            integrationUseCase.scanTag(hashMapOf("tag_id" to nfcTagId))
        } else {
            Toast.makeText(this, R.string.nfc_processing_tag_error, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
