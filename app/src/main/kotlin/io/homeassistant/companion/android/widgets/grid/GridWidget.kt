package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.EntitiesPerServer

@AndroidEntryPoint
class GridWidget : BaseGlanceEntityWidgetReceiver<GridWidgetEntity, GridWidgetDao>() {
    override val glanceAppWidget: GlanceAppWidget = GridGlanceAppWidget()

    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> = dao
        .getAll()
        .associate { widget ->
            widget.id to EntitiesPerServer(widget.serverId, widget.items.map { it.entityId })
        }
}
