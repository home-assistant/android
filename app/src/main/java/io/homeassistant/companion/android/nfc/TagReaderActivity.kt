package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.net.Uri
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

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val ndefMessage = rawMessages[0] as NdefMessage?
            mainScope.launch {
                try {
                    val url = ndefMessage?.records?.get(0)?.toUri().toString()
                    handleTag(url)
                } catch (e: Exception) {
                    val message = R.string.nfc_processing_tag_error
                    Toast.makeText(this@TagReaderActivity, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, e.message)
                    finish()
                }
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            val data: Uri? = intent?.data
            val url = data.toString()
            mainScope.launch {
                try {
                    handleTag(url)
                } catch (e: Exception) {
                    val message = R.string.qrcode_processing_tag_error
                    Toast.makeText(this@TagReaderActivity, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, e.message)
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
        // https://www.home-assistant.io/nfc/?url=homeassistant://call_service/light.turn_on?entity_id=light.extended_color_light_2
        // https://www.home-assistant.io/nfc/?url=homeassistant://fire_event/custom_event?entity_id=MY_CUSTOM_EVENT
        // https://www.home-assistant.io/tag/5f0ba733-172f-430d-a7f8-e4ad940c88d7

        val nfcTagId = UrlHandler.splitNfcTagId(url)
        if (nfcTagId != null) {
            // check if we have a nfc tag id
            integrationUseCase.scanTag(
                hashMapOf("tag_id" to nfcTagId)
            )
            finish()
        } else {
            // Check for universal link
            val haLink = UrlHandler.getUniversalLink(url)
            if (UrlHandler.isHomeAssistantUrl(haLink)) {
                val (domain, service, cs_entity) = UrlHandler.splitCallServiceLink(haLink)
                val (event, fe_entity) = UrlHandler.splitFireEventLink(haLink)

                if (domain != null && service != null && cs_entity != null) {
                    integrationUseCase.callService(
                        domain,
                        service,
                        hashMapOf("entity_id" to cs_entity)
                    )
                } else if (event != null && fe_entity != null) {
                    integrationUseCase.fireEvent(event, hashMapOf("entity_id" to fe_entity))
                }
                finish()
            }
        }
    }
}
