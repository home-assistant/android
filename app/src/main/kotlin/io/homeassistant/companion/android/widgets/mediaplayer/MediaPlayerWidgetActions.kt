package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import timber.log.Timber

@Composable
internal fun actionMediaPlayer(action: String): Action {
    return actionRunCallback<MediaPlayerAction>(actionParametersOf(ACTION_KEY to action))
}

internal val ACTION_KEY = ActionParameters.Key<String>("MEDIA_ACTION")

class MediaPlayerAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MediaPlayerActionEntryPoint {
        fun serverManager(): ServerManager
        fun dao(): MediaPlayerControlsWidgetDao
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoints = EntryPoints.get(context.applicationContext, MediaPlayerActionEntryPoint::class.java)
        val serverManager = entryPoints.serverManager()
        val dao = entryPoints.dao()
        val glanceManager = GlanceAppWidgetManager(context)
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)

        val action = parameters[ACTION_KEY] ?: return
        val widget = dao.get(appWidgetId) ?: return

        try {
            val entity = serverManager.integrationRepository(widget.serverId).getEntity(widget.entityId)
            val entityId = entity?.entityId ?: widget.entityId

            val service = when (action) {
                "play_pause" -> "media_play_pause"
                "next" -> "media_next_track"
                "previous" -> "media_previous_track"
                "volume_up" -> "volume_up"
                "volume_down" -> "volume_down"
                else -> null
            }

            if (service != null) {
                serverManager.integrationRepository(widget.serverId).callAction(
                    MEDIA_PLAYER_DOMAIN,
                    service,
                    hashMapOf("entity_id" to entityId)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to call media player action $action")
        }

        MediaPlayerGlanceAppWidget().update(context, glanceId)
    }
}
