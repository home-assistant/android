package io.homeassistant.companion.android.widgets.multi

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
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
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetButton
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElement
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetPlaintext
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetTemplate
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
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var filterByEntity = true

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
        widget_config_entity_id_text.doAfterTextChanged { updateServiceAdaptor() }
        widget_config_entity_id_text.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && view is AutoCompleteTextView)
                view.showDropDown()
        }

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

        widget_config_add_button_button.setOnClickListener {
            dynamicElementAdapter.addButton(iconDialog)
        }
        widget_config_add_plaintext_button.setOnClickListener {
            dynamicElementAdapter.addPlaintext()
        }
        widget_config_add_template_button.setOnClickListener {
            dynamicElementAdapter.addTemplate(::getTemplateTextAsync)
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
                    if (element is MultiWidgetButton) {
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
            var iconElement: MultiWidgetButton? = null
            elements.forEach { element ->
                if (element is MultiWidgetButton) {
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

    private fun getTemplateTextAsync(templateText: String, renderView: AppCompatTextView) {
        ioScope.launch {
            var renderedText: String?
            try {
                renderedText = integrationUseCase.renderTemplate(templateText, mapOf())
            } catch (e: Exception) {
                renderedText = "Error in template"
            }
            runOnUiThread {
                renderView.text = renderedText
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

            // Set up an array to store the element types
            val elementTypes = mutableListOf<MultiWidgetElement.Type>()

            // Iterate through each element and create an intent for the correct type of data
            elements.forEachIndexed { index, elem ->
                // Have each element grab its values according to its type
                elem.retrieveFinalValues(this)

                // Store element type in array for later passing
                elementTypes.add(elem.type)

                // Create an appropriate intent based on the element type
                when (elem.type) {
                    MultiWidgetElement.Type.BUTTON -> (elem as MultiWidgetButton).let {
                        // Pass service call data as extras
                        intent.apply {
                            putExtra(MultiWidget.EXTRA_DOMAIN + index, elem.domain)
                            putExtra(MultiWidget.EXTRA_SERVICE + index, elem.service)
                            putExtra(MultiWidget.EXTRA_SERVICE_DATA + index, elem.serviceData)
                            putExtra(MultiWidget.EXTRA_ICON_ID + index, elem.iconId)
                        }
                    }
                    MultiWidgetElement.Type.PLAINTEXT -> (elem as MultiWidgetPlaintext).let {
                        // Pass plaintext label and layout data as extras
                        intent.apply {
                            putExtra(MultiWidget.EXTRA_LABEL + index, elem.text)
                            putExtra(MultiWidget.EXTRA_LABEL_TEXT_SIZE + index, elem.textSize)
                            putExtra(MultiWidget.EXTRA_LABEL_MAX_LINES + index, elem.textLines)
                        }
                    }
                    MultiWidgetElement.Type.TEMPLATE -> (elem as MultiWidgetTemplate).let {
                        // Pass template string and layout data as extras
                        intent.apply {
                            putExtra(MultiWidget.EXTRA_TEMPLATE + index, elem.templateData)
                            putExtra(MultiWidget.EXTRA_TEMPLATE_TEXT_SIZE + index, elem.textSize)
                            putExtra(MultiWidget.EXTRA_TEMPLATE_MAX_LINES + index, elem.textLines)
                        }
                    }
                }
            }

            // Pass array of element types
            intent.putExtra(MultiWidget.EXTRA_ELEMENT_TYPES, elementTypes.toTypedArray())

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

    private val widgetEntityModeCheckboxListener =
        CompoundButton.OnCheckedChangeListener { _, checked ->
            if (checked) {
                widget_config_entity_id_layout.visibility = View.VISIBLE
                filterByEntity = true
            } else {
                widget_config_entity_id_layout.visibility = View.GONE
                filterByEntity = false
            }
            updateServiceAdaptor()
        }
}
