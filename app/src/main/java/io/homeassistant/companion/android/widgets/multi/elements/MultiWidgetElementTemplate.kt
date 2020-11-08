package io.homeassistant.companion.android.widgets.multi.elements

class MultiWidgetElementTemplate : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElementType
        get() = MultiWidgetElementType.TYPE_TEMPLATE

    // Create a variable for storing the template data
    lateinit var templateData: String
}
