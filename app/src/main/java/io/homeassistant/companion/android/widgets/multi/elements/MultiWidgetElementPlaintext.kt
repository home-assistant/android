package io.homeassistant.companion.android.widgets.multi.elements

import android.content.Context
import android.view.View
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.widgets.multi.MultiWidget
import kotlinx.android.synthetic.main.widget_multi_config_plaintext.view.*

class MultiWidgetElementPlaintext : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElementType
        get() = MultiWidgetElementType.TYPE_PLAINTEXT

    // Create a variable for storing the text and layout info
    lateinit var text: String
    var textSize: Int = MultiWidget.LABEL_TEXT_SMALL
    var textLines: Int = 2

    //  Create variable to store layout views
    lateinit var layout: View

    override fun retrieveFinalValues(context: Context) {
        text = layout.widget_element_label.text.toString()
        textSize = when (layout.widget_element_label_text_size.selectedItem) {
            context.resources.getString(R.string.widget_font_size_small) -> MultiWidget.LABEL_TEXT_SMALL
            context.resources.getString(R.string.widget_font_size_medium) -> MultiWidget.LABEL_TEXT_MED
            context.resources.getString(R.string.widget_font_size_large) -> MultiWidget.LABEL_TEXT_LARGE
            else -> MultiWidget.LABEL_TEXT_MED
        }
        textLines = layout.widget_element_label_text_lines.text.toString().toInt()
    }
}
