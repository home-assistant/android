package io.homeassistant.companion.android.widgets.multi.elements

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = MultiWidgetButtonEntity::class, name = "BUTTON"),
    JsonSubTypes.Type(value = MultiWidgetPlaintextEntity::class, name = "PLAINTEXT"),
    JsonSubTypes.Type(value = MultiWidgetTemplateEntity::class, name = "TEMPLATE")
)
interface MultiWidgetElementEntity {
    val widgetId: Int
    val elementId: Int
    val type: MultiWidgetElement.Type
}
