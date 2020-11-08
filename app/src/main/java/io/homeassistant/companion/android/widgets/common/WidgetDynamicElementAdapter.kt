package io.homeassistant.companion.android.widgets.common

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.icondialog.IconDialog
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.widgets.multi.MultiWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElement
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementButton
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementPlaintext
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementTemplate
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementType
import kotlinx.android.synthetic.main.widget_multi_config_button.view.*

class WidgetDynamicElementAdapter(
    private var context: MultiWidgetConfigureActivity,
    private var elements: ArrayList<MultiWidgetElement>,
    private var entities: HashMap<String, Entity<Any>>,
    private var services: HashMap<String, Service>,
    private var serviceAdapter: SingleItemArrayAdapter<Service>,
    private var entityFilterCheckbox: AppCompatCheckBox,
    private var entityIdTextView: AutoCompleteTextView
) : RecyclerView.Adapter<WidgetDynamicElementAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "MultiWidgetButtonElem"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    lateinit var iconDialog: IconDialog

    override fun getItemCount(): Int {
        return elements.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val dynamicElementLayout = inflater.inflate(
            R.layout.widget_multi_config_button,
            parent,
            false
        )

        return ViewHolder(dynamicElementLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (elements[position].type) {
            MultiWidgetElementType.TYPE_BUTTON -> bindButtonViews(
                holder.itemView,
                elements[position] as MultiWidgetElementButton
            )
            MultiWidgetElementType.TYPE_TEMPLATE -> {
            }
            MultiWidgetElementType.TYPE_PLAINTEXT -> {
            }
        }
    }

    internal fun addButton(iconDialog: IconDialog) {
        this.iconDialog = iconDialog
        elements.add(MultiWidgetElementButton())
        notifyDataSetChanged()
    }

    internal fun addTemplate() {
        elements.add(MultiWidgetElementTemplate())
        notifyDataSetChanged()
    }

    internal fun addPlaintext() {
        elements.add(MultiWidgetElementPlaintext())
        notifyDataSetChanged()
    }

    private fun bindButtonViews(dynamicElementLayout: View, element: MultiWidgetElementButton) {
        // Store layout view for icon update purposes
        element.layout = dynamicElementLayout

        // Prepare dynamic field variables
        val dynamicFields = ArrayList<ServiceFieldBinder>()
        val dynamicFieldAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFields)

        // Set up service edit text field
        dynamicElementLayout.widget_element_service_text.setAdapter(serviceAdapter)
        dynamicElementLayout.widget_element_service_text.addTextChangedListener(
            createServiceTextWatcher(
                dynamicFields,
                dynamicFieldAdapter
            )
        )
        dynamicElementLayout.widget_element_service_text.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && view is AutoCompleteTextView) {
                view.showDropDown()
            }
        }

        // Set up dynamic field layout
        dynamicElementLayout.widget_element_fields_layout.adapter = dynamicFieldAdapter
        dynamicElementLayout.widget_element_fields_layout.layoutManager =
            LinearLayoutManager(context)

        // Set up add field button
        dynamicElementLayout.widget_element_add_field_button.setOnClickListener(
            createAddFieldLowerListener(
                dynamicFields,
                dynamicFieldAdapter,
                dynamicElementLayout.widget_element_service_text
            )
        )

        // Set up icon selection button
        dynamicElementLayout.widget_element_icon_selector.setOnClickListener {
            iconDialog.show(context.supportFragmentManager, element.tag)
        }
    }

    private fun createServiceTextWatcher(
        dynamicFields: ArrayList<ServiceFieldBinder>,
        dynamicFieldAdapter: WidgetDynamicFieldAdapter
    ): TextWatcher {
        return (
                object : TextWatcher {
                    override fun afterTextChanged(p0: Editable?) {
                        val serviceText: String = p0.toString()

                        if (services.keys.contains(serviceText)) {
                            Log.d(
                                TAG,
                                "Valid domain and service--processing dynamic fields"
                            )

                            // Make sure there are not already any dynamic fields created
                            // This can happen if selecting the drop-down twice or pasting
                            dynamicFields.clear()

                            // We only call this if servicesAvailable was fetched and is not null,
                            // so we can safely assume that it is not null here
                            val fields = services[serviceText]!!.serviceData.fields
                            val fieldKeys = fields.keys
                            Log.d(
                                TAG,
                                "Fields applicable to this service: $fields"
                            )

                            fieldKeys.sorted().forEach { fieldKey ->
                                Log.d(
                                    TAG,
                                    "Creating a text input box for $fieldKey"
                                )

                                // Insert a dynamic layout
                                // IDs get priority and go at the top, since the other fields
                                // are usually optional but the ID is required
                                if (fieldKey.contains("_id")) {
                                    dynamicFields.add(
                                        0, ServiceFieldBinder(
                                            serviceText,
                                            fieldKey,
                                            if (entityFilterCheckbox.isChecked) entityIdTextView.text.toString() else null
                                        )
                                    )
                                } else
                                    dynamicFields.add(
                                        ServiceFieldBinder(
                                            serviceText,
                                            fieldKey
                                        )
                                    )
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
    }

    private fun createAddFieldLowerListener(
        dynamicFields: ArrayList<ServiceFieldBinder>,
        dynamicFieldAdapter: WidgetDynamicFieldAdapter,
        serviceTextView: AutoCompleteTextView
    ): View.OnClickListener {
        return View.OnClickListener {
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
                            serviceTextView.text.toString(),
                            fieldKeyInput.text.toString()
                        )
                    )

                    dynamicFieldAdapter.notifyDataSetChanged()
                }
                .show()
        }
    }
}
