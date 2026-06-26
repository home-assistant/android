package io.homeassistant.companion.android.widgets.climate

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.EntitiesPerServer

/**
 * Receiver for the Climate Glance Widget.
 *
 * Manages lifecycle events and entity updates for the Climate widget. Maps widget IDs to their associated entities
 * and integrates with the database via [ClimateWidgetDao].
 *
 * Note: Don't forget to register this Receiver in the manifest and in the Application.
 * Otherwise the widgets won't update at all after the composition ends.
 */
@AndroidEntryPoint
class ClimateWidget : BaseGlanceEntityWidgetReceiver<TodoWidgetEntity, TodoWidgetDao>() {       // TODO: me falta la entity y la Dao para mi widget
    override val glanceAppWidget: GlanceAppWidget = ClimateGlanceAppWidget()

    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> {
        return dao.getAll()
            .associate { widget -> widget.id to EntitiesPerServer(widget.serverId, listOf(widget.entityId)) }
    }
}
