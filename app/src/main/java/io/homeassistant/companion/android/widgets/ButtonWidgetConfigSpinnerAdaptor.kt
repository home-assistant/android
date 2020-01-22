package io.homeassistant.companion.android.widgets

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import io.homeassistant.companion.android.R
import kotlinx.android.synthetic.main.widget_image_spinner.view.*

class ButtonWidgetConfigSpinnerAdaptor(
    var context: Context,
    var icons: IntArray
) : BaseAdapter() {
    var inflator: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return icons.size
    }

    override fun getItem(p0: Int): Any {
        return context.getDrawable(icons[p0])!!
    }

    override fun getItemId(p0: Int): Long {
        return icons[p0].toLong()
    }

    override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
        // Inflate the view if it hasn't been inflated yet
        val view: View = p1 ?: inflator.inflate(R.layout.widget_image_spinner, p2, false)

        // Assign the correct icon
        view.widget_config_spinner_icon.setImageResource(icons[p0])

        return view
    }
}
