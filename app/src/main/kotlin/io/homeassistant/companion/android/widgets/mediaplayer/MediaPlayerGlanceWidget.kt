package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.EntitiesPerServer

@AndroidEntryPoint
class MediaPlayerGlanceWidget : BaseGlanceEntityWidgetReceiver<MediaPlayerControlsWidgetEntity, MediaPlayerControlsWidgetDao>() {
    override val glanceAppWidget: GlanceAppWidget = MediaPlayerGlanceAppWidget()

    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> {
        return dao.getAll()
            .associate { widget -> widget.id to EntitiesPerServer(widget.serverId, listOf(widget.entityId)) }
    }
}
