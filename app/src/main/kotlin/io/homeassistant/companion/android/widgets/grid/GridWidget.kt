package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.EntitiesPerServer

/**
 * Receiver for the Grid Glance Widget.
 *
 * Manages lifecycle events and entity updates for the grid widget. Maps widget IDs to their associated entities
 * and integrates with the database via [GridWidgetDao].
 *
 * Note: Don't forget to register this Receiver in the manifest and in the Application.
 * Otherwise the widgets won't update at all after the composition ends.
 */
@AndroidEntryPoint
class GridWidget : BaseGlanceEntityWidgetReceiver<GridWidgetEntity, GridWidgetDao>() {
    override val glanceAppWidget: GlanceAppWidget = GridGlanceAppWidget()

    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> = dao
        .getAll()
        .associate { widget ->
            widget.id to EntitiesPerServer(widget.serverId, widget.items.map { it.entityId })
        }
}
