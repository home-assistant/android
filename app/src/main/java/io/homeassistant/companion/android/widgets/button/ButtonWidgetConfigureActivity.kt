package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.data.Icon
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.databinding.WidgetButtonConfigureBinding
import io.homeassistant.companion.android.widgets.common.ServiceFieldBinder
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetDynamicFieldAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ButtonWidgetConfigureActivity : BaseActivity(), IconDialog.Callback {
    companion object {
        private const val TAG: String = "ButtonWidgetConfigAct"
        private const val ICON_DIALOG_TAG = "icon-dialog"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var iconPack: IconPack

    private var services = HashMap<String, Service>()
    private var entities = HashMap<String, Entity<Any>>()
    private var dynamicFields = ArrayList<ServiceFieldBinder>()
    private lateinit var dynamicFieldAdapter: WidgetDynamicFieldAdapter

    private lateinit var binding: WidgetButtonConfigureBinding

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var onDeleteWidget = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity
        deleteConfirmation(context)
    }

    private var onAddWidget = View.OnClickListener {
        try {
            val context = this@ButtonWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = ButtonWidget.RECEIVE_DATA
            intent.component = ComponentName(context, ButtonWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            // Analyze and send service and domain
            val serviceText = binding.widgetTextConfigService.text.toString()
            val domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
            val service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]
            intent.putExtra(
                ButtonWidget.EXTRA_DOMAIN,
                domain
            )
            intent.putExtra(
                ButtonWidget.EXTRA_SERVICE,
                service
            )

            // Fetch and send label and icon
            intent.putExtra(
                ButtonWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )
            intent.putExtra(
                ButtonWidget.EXTRA_ICON,
                binding.widgetConfigIconSelector.tag as Int
            )

            // Analyze and send service data
            val serviceDataMap = HashMap<String, Any>()
            dynamicFields.forEach {
                if (it.value != null) {
                    serviceDataMap[it.field] = it.value!!
                }
            }

            intent.putExtra(
                ButtonWidget.EXTRA_SERVICE_DATA,
                jacksonObjectMapper().writeValueAsString(serviceDataMap)
            )

            context.sendBroadcast(intent)

            // Make sure we pass back the original appWidgetId
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Issue configuring widget", e)
            Toast.makeText(applicationContext, commonR.string.widget_creation_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    private val onAddFieldListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity
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
                        binding.widgetTextConfigService.text.toString(),
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

    private val serviceTextWatcher: TextWatcher = (
        object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val serviceText: String = p0.toString()

                if (services.keys.contains(serviceText)) {
                    Log.d(TAG, "Valid domain and service--processing dynamic fields")

                    // Make sure there are not already any dynamic fields created
                    // This can happen if selecting the drop-down twice or pasting
                    dynamicFields.clear()

                    // We only call this if servicesAvailable was fetched and is not null,
                    // so we can safely assume that it is not null here
                    val serviceData = services[serviceText]!!.serviceData
                    val target = serviceData.target
                    val fields = serviceData.fields

                    val fieldKeys = fields.keys
                    Log.d(TAG, "Fields applicable to this service: $fields")

                    if (target !== false) {
                        dynamicFields.add(0, ServiceFieldBinder(serviceText, "entity_id"))
                    }

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
        }
        )

    private fun getServiceString(service: Service): String {
        return "${service.domain}.${service.service}"
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetButtonConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val buttonWidgetDao = AppDatabase.getInstance(applicationContext).buttonWidgetDao()
        val buttonWidget = buttonWidgetDao.get(appWidgetId)
        var serviceText = ""
        if (buttonWidget != null) {
            serviceText = "${buttonWidget.domain}.${buttonWidget.service}"
            binding.widgetTextConfigService.setText(serviceText)
            binding.label.setText(buttonWidget.label)
            binding.addButton.setText(commonR.string.update_widget)
            binding.deleteButton.visibility = VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        }
        // Create an icon pack loader with application context.
        val loader = IconPackLoader(this)

        val serviceAdapter = SingleItemArrayAdapter<Service>(this) {
            if (it != null) getServiceString(it) else ""
        }
        binding.widgetTextConfigService.setAdapter(serviceAdapter)
        binding.widgetTextConfigService.onFocusChangeListener = dropDownOnFocus

        mainScope.launch {
            try {
                // Fetch services
                integrationUseCase.getServices().forEach {
                    services[getServiceString(it)] = it
                }
                Log.d(TAG, "Services found: $services")
                if (buttonWidget != null) {
                    serviceAdapter.add(services[serviceText])
                    val serviceData = services[serviceText]!!.serviceData
                    val target = serviceData.target
                    val fields = serviceData.fields
                    val fieldKeys = fields.keys
                    Log.d(TAG, "Fields applicable to this service: $fields")
                    val serviceDataMap: HashMap<String, Any> =
                        jacksonObjectMapper().readValue(buttonWidget.serviceData)
                    val addedFields = mutableSetOf<String>()
                    for (item in serviceDataMap) {
                        val value: String = item.value.toString().replace("[", "").replace("]", "") + if (item.key == "entity_id") ", " else ""
                        dynamicFields.add(ServiceFieldBinder(serviceText, item.key, value))
                        addedFields.add(item.key)
                    }
                    if (target !== false && !addedFields.contains("entity_id")) {
                        dynamicFields.add(0, ServiceFieldBinder(serviceText, "entity_id"))
                    }
                    fieldKeys.sorted().forEach { fieldKey ->
                        Log.d(TAG, "Creating a text input box for $fieldKey")

                        // Insert a dynamic layout
                        // IDs get priority and go at the top, since the other fields
                        // are usually optional but the ID is required
                        if (!addedFields.contains(fieldKey)) {
                            if (fieldKey.contains("_id"))
                                dynamicFields.add(0, ServiceFieldBinder(serviceText, fieldKey))
                            else
                                dynamicFields.add(ServiceFieldBinder(serviceText, fieldKey))
                        }
                    }
                    integrationUseCase.getEntities().forEach {
                        entities[it.entityId] = it
                    }
                    dynamicFieldAdapter.notifyDataSetChanged()
                } else
                    serviceAdapter.addAll(services.values)
                serviceAdapter.sort()

                // Update service adapter
                runOnUiThread {
                    serviceAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                // Custom components can cause services to not load
                // Display error text
                Log.e(TAG, "Unable to load services from Home Assistant", e)
                binding.widgetConfigServiceError.visibility = VISIBLE
            }

            try {
                // Fetch entities
                integrationUseCase.getEntities().forEach {
                    entities[it.entityId] = it
                }
            } catch (e: Exception) {
                // If entities fail to load, it's okay to pass
                // an empty map to the dynamicFieldAdapter
            }
        }

        binding.widgetTextConfigService.addTextChangedListener(serviceTextWatcher)

        binding.addFieldButton.setOnClickListener(onAddFieldListener)
        binding.addButton.setOnClickListener(onAddWidget)

        dynamicFieldAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFields)
        binding.widgetConfigFieldsLayout.adapter = dynamicFieldAdapter
        binding.widgetConfigFieldsLayout.layoutManager = LinearLayoutManager(this)

        // Do this off the main thread, takes a second or two...
        runOnUiThread {
            // Create an icon pack and load all drawables.
            iconPack = createMaterialDesignIconPack(loader)
            iconPack.loadDrawables(loader.drawableLoader)
            val settings = IconDialogSettings {
                searchVisibility = IconDialog.SearchVisibility.ALWAYS
            }
            val iconDialog = IconDialog.newInstance(settings)
            val iconId = buttonWidget?.iconId ?: 62017
            onIconDialogIconsSelected(iconDialog, listOf(iconPack.icons[iconId]!!))
            binding.widgetConfigIconSelector.setOnClickListener {
                iconDialog.show(supportFragmentManager, ICON_DIALOG_TAG)
            }
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    override val iconDialogIconPack: IconPack?
        get() = iconPack

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<Icon>) {
        Log.d(TAG, "Selected icon: ${icons.firstOrNull()}")
        val selectedIcon = icons.firstOrNull()
        if (selectedIcon != null) {
            binding.widgetConfigIconSelector.tag = selectedIcon.id
            val iconDrawable = selectedIcon.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    DrawableCompat.setTint(icon, resources.getColor(R.color.colorIcon, theme))
                }
                binding.widgetConfigIconSelector.setImageBitmap(icon.toBitmap())
            }
        }
    }

    private fun deleteConfirmation(context: Context) {
        val buttonWidgetDao = AppDatabase.getInstance(context).buttonWidgetDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(commonR.string.confirm_delete_this_widget_title)
        builder.setMessage(commonR.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            commonR.string.confirm_positive
        ) { dialog, _ ->
            buttonWidgetDao.delete(appWidgetId)
            dialog.dismiss()
            finish()
        }

        builder.setNegativeButton(
            commonR.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
