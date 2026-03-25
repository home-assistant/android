package io.homeassistant.companion.android.common.data.mediacontrol

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.applyCompressedStateDiff
import io.homeassistant.companion.android.common.data.integration.getEntityPictureUrl
import io.homeassistant.companion.android.common.data.integration.getMediaAlbumName
import io.homeassistant.companion.android.common.data.integration.getMediaArtist
import io.homeassistant.companion.android.common.data.integration.getMediaDuration
import io.homeassistant.companion.android.common.data.integration.getMediaPosition
import io.homeassistant.companion.android.common.data.integration.getMediaTitle
import io.homeassistant.companion.android.common.data.integration.supportsNextTrack
import io.homeassistant.companion.android.common.data.integration.supportsPause
import io.homeassistant.companion.android.common.data.integration.supportsPlay
import io.homeassistant.companion.android.common.data.integration.supportsPreviousTrack
import io.homeassistant.companion.android.common.data.integration.supportsSeek
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import timber.log.Timber

internal class MediaControlRepositoryImpl @Inject constructor(
    private val prefsRepository: PrefsRepository,
    private val serverManager: ServerManager,
) : MediaControlRepository {

    override fun observeMediaControlState(): Flow<MediaControlState?> = flow<MediaControlState?> {
        val serverId = prefsRepository.getMediaControlServerId()
        val entityId = prefsRepository.getMediaControlEntityId()
        if (serverId == null || entityId == null) {
            emit(null)
            return@flow
        }

        try {
            val stateFlow = serverManager.webSocketRepository(serverId)
                .getCompressedStateAndChanges(listOf(entityId))
            if (stateFlow == null) {
                Timber.w("WebSocket subscription returned null for entity $entityId")
                emit(null)
                return@flow
            }

            var currentEntity: Entity? = null
            stateFlow.collect { event ->
                event.added?.get(entityId)?.let {
                    currentEntity = it.toEntity(entityId)
                }
                event.changed?.get(entityId)?.let { diff ->
                    currentEntity = currentEntity?.applyCompressedStateDiff(diff)
                }
                event.removed?.let { removed ->
                    if (entityId in removed) {
                        currentEntity = null
                    }
                }

                val entity = currentEntity
                if (entity != null) {
                    emit(entity.toMediaControlState(serverId = serverId))
                } else {
                    emit(null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to subscribe to media control entity $entityId")
            emit(null)
        }
    }.distinctUntilChanged()

    override suspend fun getConfiguredServerId(): Int? = prefsRepository.getMediaControlServerId()

    override suspend fun getConfiguredEntityId(): String? = prefsRepository.getMediaControlEntityId()

    override suspend fun setConfiguredEntity(serverId: Int?, entityId: String?) {
        prefsRepository.setMediaControlServerId(serverId)
        prefsRepository.setMediaControlEntityId(entityId)
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
    )
}
