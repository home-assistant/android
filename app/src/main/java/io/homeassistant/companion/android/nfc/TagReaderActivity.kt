package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.nfc.views.TagReaderView
import io.homeassistant.companion.android.util.UrlHandler
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TagReaderActivity : BaseActivity() {

    companion object {
        const val TAG = "TagReaderActivity"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                TagReaderView()
            }
        }

        lifecycleScope.launch {
            if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.action == Intent.ACTION_VIEW) {
                val isNfcTag = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED

                val url =
                    if (isNfcTag) NFCUtil.extractUrlFromNFCIntent(intent)
                    else intent.data
                try {
                    handleTag(url, isNfcTag)
                } catch (e: Exception) {
                    showProcessingError(isNfcTag)
                    Log.e(TAG, "Unable to handle url (${if (isNfcTag) "nfc" else "qr"}}): $url", e)
                }
            }
            finish()
        }
    }

    private suspend fun handleTag(url: Uri?, isNfcTag: Boolean) {
        // https://www.home-assistant.io/tag/5f0ba733-172f-430d-a7f8-e4ad940c88d7

        val nfcTagId = UrlHandler.splitNfcTagId(url)
        Log.d(TAG, "Tag ID: $nfcTagId")
        if (nfcTagId != null) {
            integrationUseCase.scanTag(hashMapOf("tag_id" to nfcTagId))
            Log.d(TAG, "Tag scanned to HA successfully")
        } else {
            showProcessingError(isNfcTag)
        }
    }

    private fun showProcessingError(isNfcTag: Boolean) {
        Toast.makeText(
            this,
            if (isNfcTag) commonR.string.nfc_processing_tag_error else commonR.string.qrcode_processing_tag_error,
            Toast.LENGTH_LONG
        ).show()
    }
}
