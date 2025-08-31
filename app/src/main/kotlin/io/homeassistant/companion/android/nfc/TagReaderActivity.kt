package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.nfc.views.TagReaderView
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TagReaderActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeAssistantAppTheme {
                TagReaderView()
            }
        }

        lifecycleScope.launch {
            if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.action == Intent.ACTION_VIEW) {
                val isNfcTag = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED

                val url =
                    if (isNfcTag) {
                        NFCUtil.extractUrlFromNFCIntent(intent)
                    } else {
                        intent.data
                    }
                try {
                    handleTag(url, isNfcTag)
                } catch (e: Exception) {
                    showProcessingError(isNfcTag)
                    Timber.e(e, "Unable to handle url (${if (isNfcTag) "nfc" else "qr"}}): $url")
                }
            }
            finish()
        }
    }

    private suspend fun handleTag(url: Uri?, isNfcTag: Boolean) {
        // https://www.home-assistant.io/tag/5f0ba733-172f-430d-a7f8-e4ad940c88d7

        val nfcTagId = UrlUtil.splitNfcTagId(url)
        Timber.d("Tag ID: $nfcTagId")
        if (nfcTagId != null && serverManager.isRegistered()) {
            serverManager.defaultServers.map {
                lifecycleScope.async {
                    try {
                        serverManager.integrationRepository(it.id).scanTag(hashMapOf("tag_id" to nfcTagId))
                        Timber.d("Tag scanned to HA successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "Tag not scanned to HA")
                    }
                }
            }.awaitAll()
        } else {
            showProcessingError(isNfcTag)
        }
    }

    private fun showProcessingError(isNfcTag: Boolean) {
        Toast.makeText(
            this,
            if (isNfcTag) commonR.string.nfc_processing_tag_error else commonR.string.qrcode_processing_tag_error,
            Toast.LENGTH_LONG,
        ).show()
    }
}
