package io.homeassistant.companion.android.widgets.grid

import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.database.widget.GridWidgetItemEntity
import io.homeassistant.companion.android.database.widget.GridWidgetWithItemsEntity
import io.homeassistant.companion.android.widgets.grid.config.GridConfiguration
import io.homeassistant.companion.android.widgets.grid.config.GridItem

fun GridConfiguration.asDbEntity(widgetId: Int) =
    GridWidgetWithItemsEntity(
        gridWidget = GridWidgetEntity(
            id = widgetId,
            serverId = serverId ?: 0,
            label = label,
            requireAuthentication = requireAuthentication
        ),
        items = items.map { it.asDbEntity(widgetId) }
    )

fun GridItem.asDbEntity(widgetId: Int) =
    GridWidgetItemEntity(
        id = id,
        gridId = widgetId,
        domain = domain,
        service = service,
        entityId = entityId,
        label = label,
        iconName = icon
    )

fun GridWidgetWithItemsEntity.asGridConfiguration() =
    GridConfiguration(
        serverId = gridWidget.serverId,
        label = gridWidget.label,
        requireAuthentication = gridWidget.requireAuthentication,
        items = items.map(GridWidgetItemEntity::asGridItem)
    )

fun GridWidgetItemEntity.asGridItem() =
    GridItem(
        id = id,
        label = label.orEmpty(),
        icon = iconName,
        domain = domain,
        service = service,
        entityId = entityId
    )
