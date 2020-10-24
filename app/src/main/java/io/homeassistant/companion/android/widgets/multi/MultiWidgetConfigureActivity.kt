package io.homeassistant.companion.android.widgets.multi

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.data.Icon
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import io.homeassistant.companion.android.widgets.common.ServiceFieldBinder
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetDynamicFieldAdapter
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_multi_configure.add_button
import kotlinx.android.synthetic.main.widget_multi_configure.add_field_button_lower
import kotlinx.android.synthetic.main.widget_multi_configure.add_field_button_upper
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_entity_id_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_fields_lower_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_fields_upper_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_label
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_label_custom_button
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_label_entity_button
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_label_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_lower_button_config_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_lower_icon_selector
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_lower_service_error
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_template_edit
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_template_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_template_render
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_upper_button_config_layout
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_upper_icon_selector
import kotlinx.android.synthetic.main.widget_multi_configure.widget_config_upper_service_error
import kotlinx.android.synthetic.main.widget_multi_configure.widget_multi_button_above_checkbox
import kotlinx.android.synthetic.main.widget_multi_configure.widget_multi_button_below_checkbox
import kotlinx.android.synthetic.main.widget_multi_configure.widget_multi_label_selector_group
import kotlinx.android.synthetic.main.widget_multi_configure.widget_multi_type_selector_group
import kotlinx.android.synthetic.main.widget_multi_configure.widget_text_config_entity_id
import kotlinx.android.synthetic.main.widget_multi_configure.widget_text_config_lower_service
import kotlinx.android.synthetic.main.widget_multi_configure.widget_text_config_upper_service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MultiWidgetConfigureActivity : AppCompatActivity(), IconDialog.Callback {
    companion object {
        private const val TAG = "MultiWidgetConfigAct"
        private const val UPPER_ICON_DIALOG_TAG = "UPPER_ICON_DIALOG_TAG"
        private const val LOWER_ICON_DIALOG_TAG = "LOWER_ICON_DIALOG_TAG"

        private const val CUSTOM_LABEL_TYPE = "CUSTOM_LABEL_TYPE"
        private const val ENTITY_LABEL_TYPE = "ENTITY_LABEL_TYPE"
        private const val TEMPLATE_LABEL_TYPE = "TEMPLATE_LABEL_TYPE"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var filterByEntity = true
    private var labelType = ENTITY_LABEL_TYPE

    private lateinit var iconPack: IconPack

    private var services = HashMap<String, Service>()
    private var entities = HashMap<String, Entity<Any>>()

    private var dynamicFieldsLower = ArrayList<ServiceFieldBinder>()
    private var dynamicFieldsUpper = ArrayList<ServiceFieldBinder>()

    private lateinit var dynamicFieldLowerAdapter: WidgetDynamicFieldAdapter
    private lateinit var dynamicFieldUpperAdapter: WidgetDynamicFieldAdapter
    private lateinit var serviceAdapter: SingleItemArrayAdapter<Service>

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override val iconDialogIconPack: IconPack?
        get() = iconPack

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_multi_configure)
        add_button.setOnClickListener(addWidgetClickListener)

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

        // Inject components
        DaggerProviderComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        // Create an icon pack loader with application context.
        val loader = IconPackLoader(this)

        widget_multi_type_selector_group.setOnCheckedChangeListener(widgetTypeSelectGroupListener)

        val entityAdapter = SingleItemArrayAdapter<Entity<Any>>(this) { it?.entityId ?: "" }
        widget_text_config_entity_id.setAdapter(entityAdapter)
        widget_text_config_entity_id.onFocusChangeListener = dropDownOnFocus

        widget_multi_button_above_checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                widget_config_upper_button_config_layout.visibility = View.VISIBLE
            } else {
                widget_config_upper_button_config_layout.visibility = View.GONE
            }
        }
        widget_multi_button_below_checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                widget_config_lower_button_config_layout.visibility = View.VISIBLE
            } else {
                widget_config_lower_button_config_layout.visibility = View.GONE
            }
        }

        serviceAdapter = SingleItemArrayAdapter<Service>(this) {
            if (it != null) getServiceString(it) else ""
        }

        widget_text_config_upper_service.setAdapter(serviceAdapter)
        widget_text_config_upper_service.onFocusChangeListener = dropDownOnFocus
        widget_text_config_upper_service.addTextChangedListener(serviceTextWatcherUpper)
        widget_text_config_lower_service.setAdapter(serviceAdapter)
        widget_text_config_lower_service.onFocusChangeListener = dropDownOnFocus
        widget_text_config_lower_service.addTextChangedListener(serviceTextWatcherLower)
        updateServiceAdaptor()

        dynamicFieldUpperAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFieldsUpper)
        dynamicFieldLowerAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFieldsLower)
        widget_config_fields_upper_layout.adapter = dynamicFieldUpperAdapter
        widget_config_fields_lower_layout.adapter = dynamicFieldLowerAdapter
        widget_config_fields_upper_layout.layoutManager = LinearLayoutManager(this)
        widget_config_fields_lower_layout.layoutManager = LinearLayoutManager(this)

        add_field_button_upper.setOnClickListener(onAddFieldUpperListener)
        add_field_button_lower.setOnClickListener(onAddFieldLowerListener)

        widget_multi_label_selector_group.setOnCheckedChangeListener(widgetLabelSelectGroupListener)

        widget_config_template_edit.addTextChangedListener(templateTextWatcher)

        add_button.setOnClickListener(addWidgetClickListener)

        mainScope.launch {
            try {
                // Fetch entities
                val fetchedEntities = integrationUseCase.getEntities()
                fetchedEntities.sortBy { e -> e.entityId }
                fetchedEntities.forEach {
                    entities[it.entityId] = it
                }
                entityAdapter.addAll(entities.values)
                entityAdapter.sort()

                runOnUiThread {
                    entityAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                // If entities fail to load, it's okay to pass
                // an empty map to the dynamicFieldAdapter
                Log.e(TAG, "Failed to query entities", e)
            }
        }

        // Do this off the main thread, takes a second or two...
        ioScope.launch {
            // Create an icon pack and load all drawables.
            iconPack = createMaterialDesignIconPack(loader)
            iconPack.loadDrawables(loader.drawableLoader)
            val settings = IconDialogSettings {
                searchVisibility = IconDialog.SearchVisibility.ALWAYS
            }
            val iconDialog = IconDialog.newInstance(settings)
            onIconDialogIconsSelected(iconDialog, listOf(iconPack.icons[62017]!!))
            widget_config_upper_icon_selector.setOnClickListener {
                iconDialog.show(supportFragmentManager, UPPER_ICON_DIALOG_TAG)
            }
            widget_config_lower_icon_selector.setOnClickListener {
                iconDialog.show(supportFragmentManager, LOWER_ICON_DIALOG_TAG)
            }
        }
    }

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<Icon>) {
        Log.d(TAG, "Selected icon: ${icons.firstOrNull()}")
        val selectedIcon = icons.firstOrNull()
        if (selectedIcon != null) {
            when (dialog.tag) {
                UPPER_ICON_DIALOG_TAG -> widget_config_upper_icon_selector.tag = selectedIcon.id
                LOWER_ICON_DIALOG_TAG -> widget_config_lower_icon_selector.tag = selectedIcon.id
            }
            val iconDrawable = selectedIcon.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    DrawableCompat.setTint(icon, resources.getColor(R.color.colorIcon, theme))
                }
                when (dialog.tag) {
                    UPPER_ICON_DIALOG_TAG -> widget_config_upper_icon_selector.setImageBitmap(icon.toBitmap())
                    LOWER_ICON_DIALOG_TAG -> widget_config_lower_icon_selector.setImageBitmap(icon.toBitmap())
                }
            }
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private fun getServiceString(service: Service): String {
        return "${service.domain}.${service.service}"
    }

    private fun updateServiceAdaptor() {
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
            } catch (e: Exception) {
                // Custom components can cause services to not load
                // Display error text
                widget_config_upper_service_error.visibility = View.VISIBLE
                widget_config_lower_service_error.visibility = View.VISIBLE
            }
        }
    }

    private val addWidgetClickListener = View.OnClickListener {
        try {
            val context = this@MultiWidgetConfigureActivity

            // Set up a broadcast intent to pass data as extras
            val intent = Intent()
            intent.action = MultiWidget.RECEIVE_DATA
            intent.component = ComponentName(context, MultiWidget::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            // If upper button is configured, pass the data for it
            if (widget_multi_button_above_checkbox.isChecked) {
                // Analyze service call for upper button
                val serviceText = context.widget_text_config_upper_service.text.toString()
                val domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
                val service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]
                val serviceDataMap = HashMap<String, Any>()
                dynamicFieldsUpper.forEach {
                    if (it.value != null) {
                        serviceDataMap[it.field] = it.value!!
                    }
                }

                // Pass service call data as extras
                intent.putExtra(MultiWidget.EXTRA_UPPER_BUTTON, true)
                intent.putExtra(MultiWidget.EXTRA_UPPER_DOMAIN, domain)
                intent.putExtra(MultiWidget.EXTRA_UPPER_SERVICE, service)
                intent.putExtra(
                    MultiWidget.EXTRA_UPPER_SERVICE_DATA,
                    jacksonObjectMapper().writeValueAsString(serviceDataMap)
                )

                // Pass icon ID
                intent.putExtra(
                    MultiWidget.EXTRA_UPPER_ICON_ID,
                    context.widget_config_upper_icon_selector.tag as Int
                )
            } else {
                intent.putExtra(MultiWidget.EXTRA_UPPER_BUTTON, false)
            }

            // If lower button is configured, pass the data for it
            if (widget_multi_button_below_checkbox.isChecked) {
                // Analyze service call for lower button
                val serviceText = context.widget_text_config_lower_service.text.toString()
                val domain = services[serviceText]?.domain ?: serviceText.split(".", limit = 2)[0]
                val service = services[serviceText]?.service ?: serviceText.split(".", limit = 2)[1]
                val serviceDataMap = HashMap<String, Any>()
                dynamicFieldsLower.forEach {
                    if (it.value != null) {
                        serviceDataMap[it.field] = it.value!!
                    }
                }

                // Pass service call data as extras
                intent.putExtra(MultiWidget.EXTRA_LOWER_BUTTON, true)
                intent.putExtra(MultiWidget.EXTRA_LOWER_DOMAIN, domain)
                intent.putExtra(MultiWidget.EXTRA_LOWER_SERVICE, service)
                intent.putExtra(
                    MultiWidget.EXTRA_LOWER_SERVICE_DATA,
                    jacksonObjectMapper().writeValueAsString(serviceDataMap)
                )

                // Pass icon ID
                intent.putExtra(
                    MultiWidget.EXTRA_LOWER_ICON_ID,
                    context.widget_config_lower_icon_selector.tag as Int
                )
            } else {
                intent.putExtra(MultiWidget.EXTRA_UPPER_BUTTON, false)
            }

            // Analyze label type
            when (labelType) {
                ENTITY_LABEL_TYPE -> {
                    // entity label type is secretly a template
                    // that just display's the entity's friendly name
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TYPE, MultiWidget.LABEL_TEMPLATE)
                    intent.putExtra(
                        MultiWidget.EXTRA_TEMPLATE,
                        "{{ states." + widget_text_config_entity_id.text.toString() + ".name }}"
                    )
                }
                TEMPLATE_LABEL_TYPE -> {
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TYPE, MultiWidget.LABEL_TEMPLATE)
                    intent.putExtra(
                        MultiWidget.EXTRA_TEMPLATE,
                        context.widget_config_template_edit.text.toString()
                    )
                }
                CUSTOM_LABEL_TYPE -> {
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TYPE, MultiWidget.LABEL_PLAINTEXT)
                    intent.putExtra(
                        MultiWidget.EXTRA_LABEL,
                        context.widget_config_label.text.toString()
                    )
                }
            }

            // Finish up and broadcast intent
            context.sendBroadcast(intent)

            // Make sure we pass back the original appWidgetId
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Issue configuring widget", e)
            Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val onAddFieldLowerListener = View.OnClickListener {
        val context = this@MultiWidgetConfigureActivity
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
                dynamicFieldsLower.add(
                    ServiceFieldBinder(
                        context.widget_text_config_lower_service.text.toString(),
                        fieldKeyInput.text.toString()
                    )
                )

                dynamicFieldLowerAdapter.notifyDataSetChanged()
            }
            .show()
    }

    private val onAddFieldUpperListener = View.OnClickListener {
        val context = this@MultiWidgetConfigureActivity
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
                dynamicFieldsUpper.add(
                    ServiceFieldBinder(
                        context.widget_text_config_upper_service.text.toString(),
                        fieldKeyInput.text.toString()
                    )
                )

                dynamicFieldUpperAdapter.notifyDataSetChanged()
            }
            .show()
    }

    private val serviceTextWatcherLower: TextWatcher = (object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            val serviceText: String = p0.toString()

            if (services.keys.contains(serviceText)) {
                Log.d(TAG, "Valid domain and service--processing dynamic fields")

                // Make sure there are not already any dynamic fields created
                // This can happen if selecting the drop-down twice or pasting
                dynamicFieldsLower.clear()

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
                        dynamicFieldsLower.add(0, ServiceFieldBinder(serviceText, fieldKey))
                    else
                        dynamicFieldsLower.add(ServiceFieldBinder(serviceText, fieldKey))
                }

                dynamicFieldUpperAdapter.notifyDataSetChanged()
            } else {
                if (dynamicFieldsUpper.size > 0) {
                    dynamicFieldsUpper.clear()
                    dynamicFieldUpperAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    })

    private val serviceTextWatcherUpper: TextWatcher = (object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            val serviceText: String = p0.toString()

            if (services.keys.contains(serviceText)) {
                Log.d(TAG, "Valid domain and service--processing dynamic fields")

                // Make sure there are not already any dynamic fields created
                // This can happen if selecting the drop-down twice or pasting
                dynamicFieldsUpper.clear()

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
                        dynamicFieldsUpper.add(0, ServiceFieldBinder(serviceText, fieldKey))
                    else
                        dynamicFieldsUpper.add(ServiceFieldBinder(serviceText, fieldKey))
                }

                dynamicFieldUpperAdapter.notifyDataSetChanged()
            } else {
                if (dynamicFieldsUpper.size > 0) {
                    dynamicFieldsUpper.clear()
                    dynamicFieldUpperAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    })

    private val templateTextWatcher: TextWatcher = (object : TextWatcher {
        override fun afterTextChanged(editableText: Editable?) {
            if (editableText == null) return

            ioScope.launch {
                var templateText: String?
                var enabled: Boolean
                try {
                    templateText =
                        integrationUseCase.renderTemplate(editableText.toString(), mapOf())
                    enabled = true
                } catch (e: Exception) {
                    templateText = "Error in template"
                    enabled = false
                }
                runOnUiThread {
                    widget_config_template_render.text = templateText
                    add_button.isEnabled = enabled
                }
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    })

    private val widgetLabelSelectGroupListener =
        RadioGroup.OnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.widget_config_label_entity_button -> {
                    widget_config_label_layout.visibility = View.GONE
                    widget_config_template_layout.visibility = View.GONE
                    labelType = ENTITY_LABEL_TYPE
                }
                R.id.widget_config_label_template_button -> {
                    widget_config_label_layout.visibility = View.GONE
                    widget_config_template_layout.visibility = View.VISIBLE
                    labelType = TEMPLATE_LABEL_TYPE
                }
                R.id.widget_config_label_custom_button -> {
                    widget_config_label_layout.visibility = View.VISIBLE
                    widget_config_template_layout.visibility = View.GONE
                    labelType = CUSTOM_LABEL_TYPE
                }
            }
        }

    private val widgetTypeSelectGroupListener = RadioGroup.OnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
            R.id.widget_multi_type_entity_button -> {
                widget_config_entity_id_layout.visibility = View.VISIBLE
                widget_config_label_entity_button.isEnabled = true
                filterByEntity = true
            }
            R.id.widget_multi_type_service_button -> {
                widget_config_entity_id_layout.visibility = View.GONE
                if (widget_config_label_entity_button.isChecked) {
                    widget_config_label_entity_button.isChecked = true
                    widget_config_label_custom_button.isChecked = true
                }
                widget_config_label_entity_button.isEnabled = false

                filterByEntity = false
            }
        }
    }
}
