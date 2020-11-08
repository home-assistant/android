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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
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
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetDynamicElementAdapter
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElement
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementButton
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementType
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_multi_config_button.view.*
import kotlinx.android.synthetic.main.widget_multi_configure.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MultiWidgetConfigureActivity : AppCompatActivity(), IconDialog.Callback {
    companion object {
        private const val TAG = "MultiWidgetConfigAct"

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
    private lateinit var iconDialog: IconDialog

    private lateinit var fetchedServices: Array<Service>
    private var services = HashMap<String, Service>()
    private var entities = HashMap<String, Entity<Any>>()

    private var elements = ArrayList<MultiWidgetElement>()
    private lateinit var dynamicElementAdapter: WidgetDynamicElementAdapter

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
        widget_config_finalize_button.setOnClickListener(addWidgetClickListener)

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

        widget_config_type_entity_checkbox.setOnCheckedChangeListener(
            widgetEntityModeCheckboxListener
        )

        val entityAdapter = SingleItemArrayAdapter<Entity<Any>>(this) { it?.entityId ?: "" }
        widget_config_entity_id_text.setAdapter(entityAdapter)
        widget_config_entity_id_text.onFocusChangeListener = dropDownOnFocus
        widget_config_entity_id_text.doAfterTextChanged { updateServiceAdaptor() }

        serviceAdapter = SingleItemArrayAdapter(this) {
            if (it != null) getServiceString(it) else ""
        }

        dynamicElementAdapter = WidgetDynamicElementAdapter(
            this,
            elements,
            entities,
            services,
            serviceAdapter,
            widget_config_type_entity_checkbox,
            widget_config_entity_id_text
        )
        widget_config_element_layout.adapter = dynamicElementAdapter
        widget_config_element_layout.layoutManager = LinearLayoutManager(this)

        widget_config_add_button_button.setOnClickListener(onAddButtonElementListener)

        widget_config_label_selector_group.setOnCheckedChangeListener(widgetLabelSelectGroupListener)

        widget_config_template_edit.addTextChangedListener(templateTextWatcher)

        widget_config_label_text_size.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.widget_label_font_size,
            android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        widget_config_finalize_button.setOnClickListener(addWidgetClickListener)

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

            try {
                // Fetch services and store for later filtering
                fetchedServices = integrationUseCase.getServices()
                updateServiceAdaptor()
            } catch (e: Exception) {
                // Custom components can cause services to not load
                // Display error text
                elements.forEach { element ->
                    if (element is MultiWidgetElementButton) {
                        element.layout.widget_element_service_error.visibility = View.VISIBLE
                    }
                }
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
            iconDialog = IconDialog.newInstance(settings)
            onIconDialogIconsSelected(iconDialog, listOf(iconPack.icons[62017]!!))
        }
    }

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<Icon>) {
        val selectedIcon = icons.firstOrNull()

        Log.d(TAG, "Selected icon: $selectedIcon")

        if (selectedIcon != null) {
            // Find which element selector the dialog should be tied to
            var iconElement: MultiWidgetElementButton? = null
            elements.forEach { element ->
                if (element is MultiWidgetElementButton) {
                    if (element.tag == dialog.tag) {
                        iconElement = element
                        return@forEach
                    }
                }
            }

            if (iconElement != null) {
                iconElement!!.iconId = selectedIcon.id

                val iconDrawable = selectedIcon.drawable
                if (iconDrawable != null) {
                    val icon = DrawableCompat.wrap(iconDrawable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        DrawableCompat.setTint(
                            icon,
                            resources.getColor(R.color.colorIcon, theme)
                        )
                    }
                    iconElement!!.layout.widget_element_icon_selector.setImageBitmap(icon.toBitmap())
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
        // Analyze fetched services
        fetchedServices.forEach {
            if (filterByEntity) {
                val domain = widget_config_entity_id_text.text
                    .toString().split(".", limit = 2)[0]

                // If we are creating an entity-focused widget,
                // only populate services associated with that widget
                if (it.domain != domain) return@forEach
            }
            services[getServiceString(it)] = it
        }

        // Clear out service adaptor or duplicates will appear
        serviceAdapter.clear()
        serviceAdapter.addAll(services.values)
        serviceAdapter.sort()

        // Update service adapter
        serviceAdapter.notifyDataSetChanged()
    }

    private val addWidgetClickListener = View.OnClickListener {
        try {
            val context = this@MultiWidgetConfigureActivity

            // Set up a broadcast intent to pass data as extras
            val intent = Intent()
            intent.action = MultiWidget.RECEIVE_DATA
            intent.component = ComponentName(context, MultiWidget::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            // Pass the number of elements to be processed
            intent.putExtra(MultiWidget.EXTRA_ELEMENT_COUNT, elements.size)

            // Iterate through each element and create an intent for the correct type of data
            elements.forEachIndexed { index, element ->
                // Have each element grab its values according to its type
                element.retrieveFinalValues()

                // Create an appropriate intent based on the element type
                when (element.type) {
                    MultiWidgetElementType.TYPE_BUTTON -> (element as MultiWidgetElementButton).let {
                        // Pass service call data as extras
                        intent.putExtra(MultiWidget.EXTRA_DOMAIN + index, element.domain)
                        intent.putExtra(MultiWidget.EXTRA_SERVICE + index, element.service)
                        intent.putExtra(MultiWidget.EXTRA_SERVICE_DATA + index, element.serviceData)
                        intent.putExtra(MultiWidget.EXTRA_ICON_ID + index, element.iconId)
                    }
                    MultiWidgetElementType.TYPE_PLAINTEXT -> {}
                    MultiWidgetElementType.TYPE_TEMPLATE -> {}
                }
            }

            // Analyze label type
            when (labelType) {
                ENTITY_LABEL_TYPE -> {
                    // Entity label type is secretly a template
                    // that just displays the entity's friendly name
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TYPE, MultiWidget.LABEL_TEMPLATE)
                    intent.putExtra(
                        MultiWidget.EXTRA_TEMPLATE,
                        "{{ states." + widget_config_entity_id_text.text.toString() + ".name }}"
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

            // Send label formatting
            when (widget_config_label_text_size.selectedItem) {
                resources.getString(R.string.widget_font_size_small) ->
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TEXT_SIZE, MultiWidget.LABEL_TEXT_SMALL)
                resources.getString(R.string.widget_font_size_medium) ->
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TEXT_SIZE, MultiWidget.LABEL_TEXT_MED)
                resources.getString(R.string.widget_font_size_large) ->
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TEXT_SIZE, MultiWidget.LABEL_TEXT_LARGE)
                else ->
                    intent.putExtra(MultiWidget.EXTRA_LABEL_TEXT_SIZE, MultiWidget.LABEL_TEXT_MED)
            }
            intent.putExtra(
                MultiWidget.EXTRA_LABEL_MAX_LINES,
                widget_config_label_text_lines.text.toString().toInt()
            )

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

    private val onAddButtonElementListener = View.OnClickListener {
        dynamicElementAdapter.addButton(iconDialog)
    }

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
                    widget_config_finalize_button.isEnabled = enabled
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

    private val widgetEntityModeCheckboxListener =
        CompoundButton.OnCheckedChangeListener { _, checked ->
            if (checked) {
                widget_config_entity_id_layout.visibility = View.VISIBLE
                widget_config_label_entity_button.isEnabled = true
                filterByEntity = true
            } else {
                widget_config_entity_id_layout.visibility = View.GONE
                if (widget_config_label_entity_button.isChecked) {
                    widget_config_label_entity_button.isChecked = true
                    widget_config_label_custom_button.isChecked = true
                }
                widget_config_label_entity_button.isEnabled = false

                filterByEntity = false
            }
            updateServiceAdaptor()
        }
}
