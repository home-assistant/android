package io.homeassistant.companion.android.widgets.multi.elements

data class MultiWidgetTemplateEntity(
    override val widgetId: Int,
    override val elementId: Int,
    val templateData: String,
    val textSize: Int,
    val maxLines: Int,
    override val type: MultiWidgetElementType = MultiWidgetElementType.TYPE_TEMPLATE
) : MultiWidgetElementEntity
