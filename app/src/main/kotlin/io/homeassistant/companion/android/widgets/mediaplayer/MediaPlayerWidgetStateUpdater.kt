package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

internal class MediaPlayerWidgetStateUpdater @Inject constructor(
    private val mediaPlayerWidgetDao: MediaPlayerControlsWidgetDao,
    private val serverManager: ServerManager,
) {

    private fun getMediaPlayerEntityOnConfigurationChange(widgetId: Int): Flow<MediaPlayerControlsWidgetEntity> {
        return mediaPlayerWidgetDao.getFlow(widgetId).filterNotNull().distinctUntilChanged()
    }

    private suspend fun getAndSubscribeEntityUpdates(serverId: Int, entityId: String): Flow<Entity?>? {
        if (serverManager.getServer(serverId) == null) return null

        val currentEntity = serverManager.integrationRepository(serverId).getEntity(entityId)
        val entityUpdateFlow = serverManager.integrationRepository(serverId).getEntityUpdates(listOf(entityId))

        return entityUpdateFlow?.onStart {
            currentEntity?.let { emit(it) }
        }
    }

    private fun getInitialStateFlow(widgetId: Int): Flow<MediaPlayerWidgetState> {
        return suspend { mediaPlayerWidgetDao.get(widgetId) }.asFlow().map {
            if (it == null) EmptyMediaPlayerState else mapToState(it, null)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFlow(widgetId: Int): Flow<MediaPlayerWidgetState> {
        val watchForChangeFlow = getMediaPlayerEntityOnConfigurationChange(widgetId)
            .flatMapLatest { widgetEntity ->
                val serverId = widgetEntity.serverId
                val entityId = widgetEntity.entityId

                getAndSubscribeEntityUpdates(serverId, entityId)?.filterNotNull()?.map { entity ->
                    mapToState(widgetEntity, entity)
                } ?: flowOf(mapToState(widgetEntity, null))
            }

        return merge(getInitialStateFlow(widgetId), watchForChangeFlow).catch {
            Timber.e(it, "Error while watching for changes for widget $widgetId")
        }.onCompletion {
            Timber.d("Stop watching for changes for widget $widgetId")
        }
    }

    private suspend fun mapToState(widget: MediaPlayerControlsWidgetEntity, entity: Entity?): MediaPlayerWidgetState {
        val baseUrl = try {
            serverManager.connectionStateProvider(widget.serverId).urlFlow().firstUrlOrNull()?.toString()?.removeSuffix("/")
        } catch (e: Exception) {
            null
        } ?: ""
        val entityPictureUrl = entity?.attributes?.get("entity_picture")?.toString()
        val fullPictureUrl = if (entityPictureUrl?.startsWith("http") == true) {
            entityPictureUrl
        } else if (entityPictureUrl != null) {
            "$baseUrl$entityPictureUrl"
        } else {
            null
        }

        return MediaPlayerStateWithData(
            serverId = widget.serverId,
            entityId = widget.entityId,
            name = entity?.friendlyName ?: widget.label,
            state = entity?.state,
            title = entity?.attributes?.get("media_title")?.toString(),
            artist = (entity?.attributes?.get("media_artist") ?: entity?.attributes?.get("media_album_artist"))?.toString(),
            album = entity?.attributes?.get("media_album_name")?.toString(),
            entityPictureUrl = fullPictureUrl,
            showSkip = widget.showSkip,
            showSeek = widget.showSeek,
            showVolume = widget.showVolume,
            showSource = widget.showSource,
            backgroundType = widget.backgroundType,
            textColor = widget.textColor
        )
    }
}
