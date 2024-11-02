package io.homeassistant.companion.android.widgets.grid

import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.widgets.grid.config.GridConfiguration
import io.homeassistant.companion.android.widgets.grid.config.GridItem

fun GridConfiguration.asDbEntity(widgetId: Int) = GridWidgetEntity(
    id = widgetId,
    serverId = serverId,
    label = label,
    items = items.map { it.asDbEntity(widgetId) },
)

fun GridItem.asDbEntity(widgetId: Int) = GridWidgetEntity.Item(
    gridId = widgetId,
    entityId = entityId,
    label = label,
    iconName = icon,
)

fun GridWidgetEntity.asGridConfiguration() = GridConfiguration(
    serverId = serverId,
    label = label,
    items = items.map(GridWidgetEntity.Item::asGridItem),
)

fun GridWidgetEntity.Item.asGridItem() = GridItem(
    label = label,
    icon = iconName,
    entityId = entityId,
)
