package io.homeassistant.companion.android.common.data.mediacontrol

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.applyCompressedStateDiff
import io.homeassistant.companion.android.common.data.integration.getEntityPictureUrl
import io.homeassistant.companion.android.common.data.integration.getMediaAlbumName
import io.homeassistant.companion.android.common.data.integration.getMediaArtist
import io.homeassistant.companion.android.common.data.integration.getMediaDuration
import io.homeassistant.companion.android.common.data.integration.getMediaPosition
import io.homeassistant.companion.android.common.data.integration.getMediaTitle
import io.homeassistant.companion.android.common.data.integration.getVolumeMuted
import io.homeassistant.companion.android.common.data.integration.supportsNextTrack
import io.homeassistant.companion.android.common.data.integration.supportsPause
import io.homeassistant.companion.android.common.data.integration.supportsPlay
import io.homeassistant.companion.android.common.data.integration.supportsPreviousTrack
import io.homeassistant.companion.android.common.data.integration.supportsSeek
import io.homeassistant.companion.android.common.data.integration.supportsVolumeSet
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import timber.log.Timber

internal class MediaControlRepositoryImpl @Inject constructor(
    private val prefsRepository: PrefsRepository,
    private val serverManager: ServerManager,
) : MediaControlRepository {

    override fun observeEntityState(config: MediaControlEntityConfig): Flow<MediaControlState?> =
        flow<MediaControlState?> {
            try {
                val stateFlow = serverManager.webSocketRepository(config.serverId)
                    .getCompressedStateAndChanges(listOf(config.entityId))
                if (stateFlow == null) {
                    Timber.w("WebSocket subscription returned null for entity ${config.entityId}")
                    emit(null)
                    return@flow
                }

                var currentEntity: Entity? = null
                stateFlow.collect { event ->
                    event.added?.get(config.entityId)?.let {
                        currentEntity = it.toEntity(config.entityId)
                    }
                    event.changed?.get(config.entityId)?.let { diff ->
                        currentEntity = currentEntity?.applyCompressedStateDiff(diff)
                    }
                    event.removed?.let { removed ->
                        if (config.entityId in removed) {
                            currentEntity = null
                        }
                    }

                    val entity = currentEntity
                    if (entity != null) {
                        emit(entity.toMediaControlState(serverId = config.serverId))
                    } else {
                        emit(null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to subscribe to media control entity ${config.entityId}")
                emit(null)
            }
        }.distinctUntilChanged()

    override fun observeMediaControlStates(): Flow<List<MediaControlState>> = flow {
        val entities = prefsRepository.getMediaControlEntities()
        if (entities.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val perEntityFlows = entities.map { config -> observeEntityState(config) }
        combine(perEntityFlows) { states -> states.filterNotNull() }
            .distinctUntilChanged()
            .collect { emit(it) }
    }

    override suspend fun getConfiguredEntities(): List<MediaControlEntityConfig> =
        prefsRepository.getMediaControlEntities()

    override suspend fun setConfiguredEntities(entities: List<MediaControlEntityConfig>) {
        prefsRepository.setMediaControlEntities(entities)
    }
}

private fun Entity.toMediaControlState(serverId: Int): MediaControlState {
    val playbackState = when (state) {
        "playing" -> MediaPlaybackState.Playing
        "paused" -> MediaPlaybackState.Paused
        "buffering" -> MediaPlaybackState.Buffering
        "idle", "standby" -> MediaPlaybackState.Idle
        else -> MediaPlaybackState.Off
    }

    return MediaControlState(
        entityId = entityId,
        serverId = serverId,
        playbackState = playbackState,
        title = getMediaTitle(),
        artist = getMediaArtist(),
        albumName = getMediaAlbumName(),
        entityPictureUrl = getEntityPictureUrl(),
        mediaDurationSeconds = getMediaDuration(),
        mediaPositionSeconds = getMediaPosition(),
        supportsPause = supportsPause(),
        supportsPlay = supportsPlay(),
        supportsSeek = supportsSeek(),
        supportsPreviousTrack = supportsPreviousTrack(),
        supportsNextTrack = supportsNextTrack(),
        supportsVolumeSet = supportsVolumeSet(),
        volumeLevel = if (supportsVolumeSet()) (attributes["volume_level"] as? Number)?.toFloat() else null,
        isVolumeMuted = getVolumeMuted(),
        entityFriendlyName = attributes["friendly_name"] as? String,
    )
}
