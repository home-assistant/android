package io.homeassistant.companion.android.widgets.multi.elements

import android.content.Context
import android.view.View

interface MultiWidgetElement {
    enum class Type {
        BUTTON, PLAINTEXT, TEMPLATE
    }

    val type: Type

    //  Create variable to store layout views
    var layout: View

    fun retrieveFinalValues(context: Context)
}
