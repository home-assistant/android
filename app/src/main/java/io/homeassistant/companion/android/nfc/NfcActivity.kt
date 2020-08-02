package io.homeassistant.companion.android.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Service
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.widgets.ServiceFieldBinder
import io.homeassistant.companion.android.widgets.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.WidgetDynamicFieldAdapter
import kotlinx.android.synthetic.main.activity_nfc.*
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class NfcActivity : AppCompatActivity() {
    private val TAG: String = "NfcActivity"

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, NfcActivity::class.java)
        }
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private var services = HashMap<String, Service>()
    private var entities = HashMap<String, Entity<Any>>()
    private var dynamicFields = ArrayList<ServiceFieldBinder>()
    private lateinit var dynamicFieldAdapter: WidgetDynamicFieldAdapter

    private var nfcUrlToWrite: String? = null

    private var mNfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)


        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            nfc_tag_id_setup.visibility = View.GONE
            nfc_universal_link_setup.visibility = View.GONE
            mode_switch.visibility = View.GONE

            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val ndefMessage = rawMessages[0] as NdefMessage?
            mainScope.launch {
                handleNFCTag(ndefMessage)
            }
        } else {
            nfc_loading.visibility = View.GONE
            nfc_tag_id_setup.visibility = View.GONE
            nfc_universal_link_setup.visibility = View.GONE


            initUi();
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this)

            add_nfc_tag_id.setOnClickListener {
                mode_switch.visibility = View.GONE
                nfc_tag_id_setup.visibility = View.VISIBLE
            }

            add_universal_link.setOnClickListener {
                mode_switch.visibility = View.GONE
                nfc_universal_link_setup.visibility = View.VISIBLE
            }
        }
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
                "mobile_app.nfc_tag_read",
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

    private fun initUi() {
        val serviceAdapter = SingleItemArrayAdapter<Service>(this) {
            if (it != null) getServiceString(it) else ""
        }
        widget_text_config_service.setAdapter(serviceAdapter)
        widget_text_config_service.onFocusChangeListener = dropDownOnFocus

        mainScope.launch {
            try {
                // Fetch services
                integrationUseCase.getServices().forEach {
                    services[getServiceString(it)] = it
                }
                serviceAdapter.addAll(services.values)
                serviceAdapter.sort()

                // Update service adapter
                runOnUiThread {
                    serviceAdapter.notifyDataSetChanged()
                }

                // Fetch entities
                integrationUseCase.getEntities().forEach {
                    entities[it.entityId] = it
                }
            } catch (e: Exception) {
                // Custom components can cause services to not load
                // Display error text
                widget_config_service_error.visibility = View.VISIBLE
            }
        }

        widget_text_config_service.addTextChangedListener(serviceTextWatcher)

        add_field_button.setOnClickListener(onAddFieldListener)
        add_button.setOnClickListener(onClickListener)

        dynamicFieldAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFields)
        widget_config_fields_layout.adapter = dynamicFieldAdapter
        widget_config_fields_layout.layoutManager = LinearLayoutManager(this)


        write_tag_id.setOnClickListener {
            nfcUrlToWrite = "https://www.home-assistant.io/nfc/${UUID.randomUUID()}"
            Toast.makeText(applicationContext, R.string.nfc_write_tag_info, Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        mNfcAdapter?.let {
            NFCUtil.enableNFCInForeground(it, this,javaClass)
        }
    }

    override fun onPause() {
        super.onPause()
        mNfcAdapter?.let {
            NFCUtil.disableNFCInForeground(it,this)
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }


    private fun String.utf8(): String = java.net.URLEncoder.encode(this, "UTF-8")
    private var onClickListener = View.OnClickListener {
        try {
            val context = this@NfcActivity

            // Analyze and send service and domain
            val serviceText = context.widget_text_config_service.text.toString()
            val domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
            val service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]

            // Analyze and send service data
            val serviceDataMap = HashMap<String, List<Any>>()
            dynamicFields.forEach {
                if (it.value != null) {
                    serviceDataMap[it.field] = it.value!! as List<Any>
                }
            }

            val params = serviceDataMap.map {(k, v) -> "${k.utf8()}=${v[0]}"}.joinToString("&")

            nfcUrlToWrite =
                "https://www.home-assistant.io/nfc/?url=homeassistant://call_service/$domain.$service?$params"
            Log.d(TAG, "Generated url: $nfcUrlToWrite")
            Toast.makeText(applicationContext, R.string.nfc_write_tag_info, Toast.LENGTH_LONG)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Issue configuring nfc tag", e)
            Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    private val onAddFieldListener = View.OnClickListener {
        val context = this@NfcActivity
        val fieldKeyInput = EditText(context)
        fieldKeyInput.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        AlertDialog.Builder(context)
            .setTitle("Field")
            .setView(fieldKeyInput)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                dynamicFields.add(
                    ServiceFieldBinder(
                        context.widget_text_config_service.text.toString(),
                        fieldKeyInput.text.toString()
                    )
                )

                dynamicFieldAdapter.notifyDataSetChanged()
            }
            .show()
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val serviceTextWatcher: TextWatcher = (object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            val serviceText: String = p0.toString()

            if (services.keys.contains(serviceText)) {
                Log.d(TAG, "Valid domain and service--processing dynamic fields")

                // Make sure there are not already any dynamic fields created
                // This can happen if selecting the drop-down twice or pasting
                dynamicFields.clear()

                // We only call this if servicesAvailable was fetched and is not null,
                // so we can safely assume that it is not null here
                val fields = services[serviceText]!!.serviceData.fields
                val fieldKeys = fields.keys
                Log.d(TAG, "Fields applicable to this service: $fields")

                fieldKeys.sorted().forEach { fieldKey ->
                    Log.d(TAG, "Creating a text input box for $fieldKey")

                    // Insert a dynamic layout
                    // IDs get priority and go at the top, since the other fields
                    // are usually optional but the ID is required
                    if (fieldKey.contains("_id"))
                        dynamicFields.add(0, ServiceFieldBinder(serviceText, fieldKey))
                    else
                        dynamicFields.add(ServiceFieldBinder(serviceText, fieldKey))
                }

                dynamicFieldAdapter.notifyDataSetChanged()
            } else {
                if (dynamicFields.size > 0) {
                    dynamicFields.clear()
                    dynamicFieldAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    })

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            // Create new nfc tag
            if(nfcUrlToWrite == null) {
                Toast.makeText(applicationContext, R.string.nfc_write_tag_too_early, Toast.LENGTH_LONG).show()
            } else {
                try {
                    NFCUtil.createNFCMessage(nfcUrlToWrite!!, intent)
                    Log.d(TAG, "Wrote nfc tag with url: $nfcUrlToWrite")
                    val message = R.string.nfc_write_tag_success
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    finish()
                } catch (err: java.lang.Exception) {
                    val message = R.string.nfc_write_tag_error
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getServiceString(service: Service): String {
        return "${service.domain}.${service.service}"
    }
}