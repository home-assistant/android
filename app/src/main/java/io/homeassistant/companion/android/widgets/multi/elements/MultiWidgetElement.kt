package io.homeassistant.companion.android.widgets.multi.elements

import android.content.Context

interface MultiWidgetElement {
    enum class Type {
        BUTTON, PLAINTEXT, TEMPLATE
    }

    val type: Type

    fun retrieveFinalValues(context: Context)
}
