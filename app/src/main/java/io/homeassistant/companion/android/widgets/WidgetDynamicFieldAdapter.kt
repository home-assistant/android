package io.homeassistant.companion.android.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.Service
import kotlinx.android.synthetic.main.widget_button_configure_dynamic_field.view.*

class WidgetDynamicFieldAdapter(
    private val services: HashMap<String, Service>,
    private val entities: HashMap<String, Entity<Any>>,
    private val serviceFieldList: ArrayList<Pair<String, String>>
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

        val serviceText: String = serviceFieldList[position].first
        val fieldKey = serviceFieldList[position].second

        // Set label for the text view
        // Reformat text to "Capital Words" intead of "capital_words"
        dynamicFieldLayout.dynamic_autocomplete_label.text =
            fieldKey.split("_").map {
                if (it == "id") it.toUpperCase()
                else it.capitalize()
            }.joinToString(" ")

        // If field is looking for an entity_id,
        // populate the autocomplete with the list of entities
        if (fieldKey == "entity_id" && entities.isNotEmpty()) {
            // Only populate with entities for the domain
            // or for homeassistant domain, which should be able
            // to manipulate entities in any domain
            val domain = services[serviceText]!!.domain
            val domainEntities: ArrayList<String> = ArrayList()
            if (domain == ("homeassistant")) {
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
            autoCompleteTextView.onFocusChangeListener = dropDownOnFocus
        } else if (services[serviceText]!!.serviceData.fields[fieldKey]!!.values != null) {
            // If a non-"entity_id" field has specific values,
            // populate the autocomplete with valid values
            val fieldAdapter = SingleItemArrayAdapter<String>(context) { it!! }
            fieldAdapter.addAll(
                services[serviceText]!!.serviceData.fields[fieldKey]!!.values!!.sorted().toMutableList()
            )
            autoCompleteTextView.setAdapter(fieldAdapter)
            autoCompleteTextView.onFocusChangeListener = dropDownOnFocus
        }
    }
}
