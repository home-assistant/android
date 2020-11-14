package io.homeassistant.companion.android.widgets.multi.elements

data class MultiWidgetButtonEntity(
    override val widgetId: Int,
    override val elementId: Int,
    val domain: String,
    val service: String,
    val serviceData: String,
    val iconId: Int,
    override val type: MultiWidgetElementType = MultiWidgetElementType.TYPE_BUTTON
) : MultiWidgetElementEntity
