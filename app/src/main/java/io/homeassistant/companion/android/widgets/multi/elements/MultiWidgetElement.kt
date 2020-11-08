package io.homeassistant.companion.android.widgets.multi.elements

interface MultiWidgetElement {
    val type: MultiWidgetElementType

    fun retrieveFinalValues()
}
