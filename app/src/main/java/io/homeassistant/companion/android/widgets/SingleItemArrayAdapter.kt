package io.homeassistant.companion.android.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class SingleItemArrayAdapter<T>(
    context: Context,
    private val createText: (T?) -> String
) : ArrayAdapter<T>(context, android.R.layout.simple_list_item_1) {
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: View.inflate(context, android.R.layout.simple_list_item_1, null)
        val item = getItem(position)
        (view as TextView).text = createText(item)
        return view
    }
}
