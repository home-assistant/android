package io.homeassistant.companion.android.widgets.multi.elements

data class MultiWidgetTemplateEntity(
    override val widgetId: Int,
    override val elementId: Int,
    val templateData: String,
    val textSize: Int,
    val maxLines: Int,
    override val type: MultiWidgetElement.Type = MultiWidgetElement.Type.TEMPLATE
) : MultiWidgetElementEntity
