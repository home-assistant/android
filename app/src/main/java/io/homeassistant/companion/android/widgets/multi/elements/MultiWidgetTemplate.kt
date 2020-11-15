package io.homeassistant.companion.android.widgets.multi.elements

import android.content.Context
import android.view.View
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.widgets.multi.MultiWidget
import kotlinx.android.synthetic.main.widget_multi_config_template.view.*

class MultiWidgetTemplate : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElement.Type
        get() = MultiWidgetElement.Type.TEMPLATE

    // Create a variable for storing the template data and layout info
    lateinit var templateData: String
    var textSize: Int = MultiWidget.LABEL_TEXT_SMALL
    var textLines: Int = 2

    //  Create variable to store layout views
    override lateinit var layout: View

    override fun retrieveFinalValues(context: Context) {
        templateData = layout.widget_element_template_edit.text.toString()
        textSize = when (layout.widget_element_template_text_size.selectedItem) {
            context.resources.getString(R.string.widget_font_size_small) -> MultiWidget.LABEL_TEXT_SMALL
            context.resources.getString(R.string.widget_font_size_medium) -> MultiWidget.LABEL_TEXT_MED
            context.resources.getString(R.string.widget_font_size_large) -> MultiWidget.LABEL_TEXT_LARGE
            else -> MultiWidget.LABEL_TEXT_MED
        }

        textLines = layout.widget_element_template_text_lines.text.toString().toInt()

        // If textLines is 0, we actually want no limit on the number of lines
        if (textLines == 0) textLines = Integer.MAX_VALUE
    }
}
