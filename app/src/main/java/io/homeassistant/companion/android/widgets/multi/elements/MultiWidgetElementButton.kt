package io.homeassistant.companion.android.widgets.multi.elements

import android.view.View

class MultiWidgetElementButton : MultiWidgetElement {
    // Define the type of the element
    override val type: MultiWidgetElementType
        get() = MultiWidgetElementType.TYPE_BUTTON

    // Set default icon ID
    var iconId: Int = 62017

    // Create a random tag for identifying the icon dialog
    val tag: String = java.util.UUID.randomUUID().toString()

    // Create variables to store service call information
    lateinit var domain: String
    lateinit var service: String
    lateinit var serviceData: String

    //  Create variable to store layout view
    lateinit var layout: View
}
