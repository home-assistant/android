package io.homeassistant.companion.android.widgets.multi.elements

data class MultiWidgetPlaintextEntity(
    override val widgetId: Int,
    override val elementId: Int,
    val text: String,
    val textSize: Int,
    val maxLines: Int,
    override val type: MultiWidgetElementType = MultiWidgetElementType.TYPE_PLAINTEXT
) : MultiWidgetElementEntity
