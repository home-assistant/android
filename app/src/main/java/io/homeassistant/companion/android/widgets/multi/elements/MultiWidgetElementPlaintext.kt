package io.homeassistant.companion.android.widgets.multi.elements

class MultiWidgetElementPlaintext : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElementType
        get() = MultiWidgetElementType.TYPE_PLAINTEXT

    // Create a variable for storing the text
    lateinit var text: String

    override fun retrieveFinalValues() {
        // TODO
    }
}
