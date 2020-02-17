package io.homeassistant.companion.android.widgets

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.Service
import java.lang.Exception
import kotlinx.android.synthetic.main.widget_button_configure_dynamic_field.view.*

class WidgetDynamicFieldAdapter(
    private val services: HashMap<String, Service>,
    private val entities: HashMap<String, Entity<Any>>,
    private val serviceFieldList: ArrayList<ServiceFieldBinder>
) : RecyclerView.Adapter<WidgetDynamicFieldAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    override fun getItemCount(): Int {
        return serviceFieldList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val dynamicFieldLayout = inflater.inflate(
            io.homeassistant.companion.android.R.layout.widget_button_configure_dynamic_field,
            parent,
            false
        )

        return ViewHolder(dynamicFieldLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dynamicFieldLayout = holder.itemView
        val autoCompleteTextView = dynamicFieldLayout.dynamic_autocomplete_textview
        val context = dynamicFieldLayout.context

        val serviceText: String = serviceFieldList[position].service
        val fieldKey = serviceFieldList[position].field

        // Set label for the text view
        // Reformat text to "Capital Words" instead of "capital_words"
        dynamicFieldLayout.dynamic_autocomplete_label.text =
            fieldKey.split("_").map {
                if (it == "id") it.toUpperCase()
                else it.capitalize()
            }.joinToString(" ")

        // If the field has an example, use it as a hint
        if (services[serviceText]?.serviceData?.fields?.get(fieldKey)?.example != null) {
            try {
                // Fetch example text
                var exampleText =
                    services[serviceText]?.serviceData?.fields?.get(fieldKey)?.example.toString()

                // Strip of brackets if the example is a list
                // Lists can be entered as comma-separated strings
                // e.g. 255, 255, 0 instead of [255, 255, 0]
                if (exampleText[0] == '[' &&
                    exampleText[exampleText.length - 1] == ']'
                ) {
                    exampleText = exampleText.subSequence(1, exampleText.length - 1).toString()
                }

                // Set example as hint
                autoCompleteTextView.hint = exampleText
            } catch (e: Exception) {
                // Who knows what custom components will break here
            }
        }

        // If field is looking for an entity_id,
        // populate the autocomplete with the list of entities
        if (fieldKey == "entity_id" && entities.isNotEmpty()) {
            val domainEntities: ArrayList<String> = ArrayList()

            // Only populate with entities for the domain
            // or for homeassistant domain, which should be able
            // to manipulate entities in any domain
            val domain = services[serviceText]?.domain

            // Add all as an available entity
            // all is a special keyword, so it won't be listed in any
            // domains even though it is available for all of them
            domainEntities.add("all")

            if (domain == ("homeassistant") || domain == null) {
                domainEntities.addAll(entities.keys)
            } else {
                entities.keys.forEach {
                    if (it.startsWith(domain) || it.startsWith("group")) {
                        domainEntities.add(it)
                    }
                }
            }

            val adapter = SingleItemArrayAdapter<String>(context) { it!! }
            adapter.addAll(domainEntities.sorted().toMutableList())
            autoCompleteTextView.setAdapter(adapter)
            autoCompleteTextView.setTokenizer(CommaTokenizer())
            autoCompleteTextView.onFocusChangeListener = dropDownOnFocus
        } else if (services[serviceText]?.serviceData?.fields?.get(fieldKey)?.values != null) {
            // If a non-"entity_id" field has specific values,
            // populate the autocomplete with valid values
            val fieldAdapter = SingleItemArrayAdapter<String>(context) { it!! }
            fieldAdapter.addAll(
                services[serviceText]!!.serviceData.fields.getValue(fieldKey).values!!.sorted().toMutableList()
            )
            autoCompleteTextView.setAdapter(fieldAdapter)
            autoCompleteTextView.setTokenizer(CommaTokenizer())
            autoCompleteTextView.onFocusChangeListener = dropDownOnFocus
        }

        // Populate textview with stored text for that field
        // Currently value can by Any? but will currently only be storing String?
        // This may have to be changed later if multi-select gets implemented
        if (serviceFieldList[position].value != null) {
            serviceFieldList[position].value as String
        }

        // Have the text view store its text for later recall
        autoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                // Don't store data that's empty (or just whitespace)
                if (!p0.isNullOrBlank()) {
                    serviceFieldList[position].value = p0.toString().toJsonType()
                } else {
                    serviceFieldList[position].value = null
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    private fun String.toJsonType(): Any? {
        // Parse the string to one of the following
        // valid base JSON types:
        // array, number, boolean, string

        // Check if the text is an array
        if (this.contains(",")) {
            val jsonArray = ArrayList<Any>()

            this.split(",").forEach { subString ->
                // Ignore whitespace
                if (!subString.isBlank()) {
                    subString.trim().toJsonType()?.let { jsonArray.add(it) }
                }
            }

            // If the array didn't contain anything
            // but commas, return null
            if (jsonArray.size == 0) return null

            return jsonArray.toList()
        }

        // Parse the base types
        this.trim().let { trimmedStr ->
            trimmedStr.toIntOrNull()?.let { return it }
            trimmedStr.toDoubleOrNull()?.let { return it }
            trimmedStr.toBooleanOrNull()?.let { return it }
            return this
        }
    }

    private fun String.toBooleanOrNull(): Boolean? {
        // Parse all valid YAML boolean values
        return when (this.trim().toLowerCase()) {
            "true" -> true
            "on" -> true
            "yes" -> true
            "y" -> true

            "false" -> false
            "off" -> false
            "no" -> false
            "n" -> false

            // If it's not a valid YAML boolean, return null
            else -> null
        }
    }
}
