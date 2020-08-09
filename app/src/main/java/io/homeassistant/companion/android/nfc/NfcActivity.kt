package io.homeassistant.companion.android.nfc

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
import kotlinx.coroutines.*
import javax.inject.Inject

class NfcActivity : AppCompatActivity() {

    val TAG = NfcActivity::class.simpleName

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

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
                    handleNFCTag(ndefMessage)
                } catch (e: Exception) {
                    val message = R.string.nfc_processing_tag_error
                    Toast.makeText(this@NfcActivity, message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, e.message)
                    finish()
                }
            }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleNFCTag(ndefMessage: NdefMessage?) {
        val url = ndefMessage?.records?.get(0)?.toUri().toString()
        // https://www.home-assistant.io/nfc/?url=homeassistant://call_service/light.turn_on?entity_id=light.extended_color_light_2
        // https://www.home-assistant.io/nfc/?url=homeassistant://fire_event/custom_event?entity_id=MY_CUSTOM_EVENT
        // https://www.home-assistant.io/nfc/5f0ba733-172f-430d-a7f8-e4ad940c88d7

        val nfcTagId = UrlHandler.splitNfcTagId(url)
        if (nfcTagId != null) {
            // check if we have a nfc tag id
            val deviceName = integrationUseCase.getRegistration().deviceName!!
            integrationUseCase.fireEvent(
                "nfc.tag_read",
                hashMapOf("tag" to nfcTagId, "device_name" to deviceName)
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
