package io.homeassistant.companion.android.widgets.multi.elements

import android.content.Context

interface MultiWidgetElement {
    val type: MultiWidgetElementType

    fun retrieveFinalValues(context: Context)
}
