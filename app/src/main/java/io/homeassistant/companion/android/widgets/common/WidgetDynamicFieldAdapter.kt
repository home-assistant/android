package io.homeassistant.companion.android.widgets.common

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.common.util.capitalize
import io.homeassistant.companion.android.databinding.WidgetButtonConfigureDynamicFieldBinding
import java.util.Locale
import kotlin.Exception

class WidgetDynamicFieldAdapter(
    private var services: HashMap<String, Service>,
    private var entities: HashMap<String, Entity<Any>>,
    private val serviceFieldList: ArrayList<ServiceFieldBinder>
) : RecyclerView.Adapter<WidgetDynamicFieldAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "WidgetField"
    }

    class ViewHolder(
        val binding: WidgetButtonConfigureDynamicFieldBinding
    ) : RecyclerView.ViewHolder(binding.root)

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

        val binding = WidgetButtonConfigureDynamicFieldBinding.inflate(inflater, parent, false)

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        val autoCompleteTextView = binding.dynamicAutocompleteTextview
        val context = holder.itemView.context

        val serviceText: String = serviceFieldList[position].service
        val fieldKey = serviceFieldList[position].field

        // Set label for the text view
        // Reformat text to "Capital Words" instead of "capital_words"
        binding.dynamicAutocompleteLabel.text =
            fieldKey.split("_").joinToString(" ") {
                if (it == "id") {
                    it.uppercase(Locale.getDefault())
                } else {
                    it.capitalize(Locale.getDefault())
                }
            }

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
        } else {
            autoCompleteTextView.hint = null
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
        } else {
            autoCompleteTextView.setAdapter(null)
            autoCompleteTextView.setTokenizer(null)
            autoCompleteTextView.onFocusChangeListener = null
        }

        // Populate textview with stored text for that field
        // Currently value can by Any? but will currently only be storing String?
        // This may have to be changed later if multi-select gets implemented
        if (serviceFieldList[position].value != null) {
            try {
                autoCompleteTextView.setText(serviceFieldList[position].value as String)
            } catch (e: Exception) {
                Log.d(TAG, "Unable to get service field list", e)
                // Set text to empty string to prevent a recycled, incorrect value
                autoCompleteTextView.setText("")
            }
        } else {
            autoCompleteTextView.setText("")
        }

        // Have the text view store its text for later recall
        autoCompleteTextView.doAfterTextChanged {
            // Only attempt to store data if we are in bounds
            if (serviceFieldList.size >= holder.bindingAdapterPosition &&
                holder.bindingAdapterPosition != RecyclerView.NO_POSITION
            ) {
                // Don't store data that's empty (or just whitespace)
                if (it.isNullOrBlank()) {
                    serviceFieldList[holder.bindingAdapterPosition].value = null
                } else {
                    serviceFieldList[holder.bindingAdapterPosition].value = it.toString().toJsonType()
                }
            }
        }
    }

    fun replaceValues(services: HashMap<String, Service>, entities: HashMap<String, Entity<Any>>) {
        this.services = services
        this.entities = entities
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
                if (subString.isNotBlank()) {
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

    private fun String.toBooleanOrNull(): Boolean? = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
