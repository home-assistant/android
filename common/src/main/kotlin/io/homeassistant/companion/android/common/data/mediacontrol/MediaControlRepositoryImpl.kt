package io.homeassistant.companion.android.common.data.mediacontrol

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.applyCompressedStateDiff
import io.homeassistant.companion.android.common.data.integration.getAppName
import io.homeassistant.companion.android.common.data.integration.getEntityPictureUrl
import io.homeassistant.companion.android.common.data.integration.getMediaAlbumArtist
import io.homeassistant.companion.android.common.data.integration.getMediaAlbumName
import io.homeassistant.companion.android.common.data.integration.getMediaArtist
import io.homeassistant.companion.android.common.data.integration.getMediaChannel
import io.homeassistant.companion.android.common.data.integration.getMediaContentType
import io.homeassistant.companion.android.common.data.integration.getMediaDuration
import io.homeassistant.companion.android.common.data.integration.getMediaPosition
import io.homeassistant.companion.android.common.data.integration.getMediaSeriesTitle
import io.homeassistant.companion.android.common.data.integration.getMediaTitle
import io.homeassistant.companion.android.common.data.integration.getMediaTrack
import io.homeassistant.companion.android.common.data.integration.getShuffle
import io.homeassistant.companion.android.common.data.integration.getVolumeMuted
import io.homeassistant.companion.android.common.data.integration.supportsNextTrack
import io.homeassistant.companion.android.common.data.integration.supportsPause
import io.homeassistant.companion.android.common.data.integration.supportsPlay
import io.homeassistant.companion.android.common.data.integration.supportsPreviousTrack
import io.homeassistant.companion.android.common.data.integration.supportsRepeatSet
import io.homeassistant.companion.android.common.data.integration.supportsSeek
import io.homeassistant.companion.android.common.data.integration.supportsShuffleSet
import io.homeassistant.companion.android.common.data.integration.supportsStop
import io.homeassistant.companion.android.common.data.integration.supportsVolumeMute
import io.homeassistant.companion.android.common.data.integration.supportsVolumeSet
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.mediacontrol.MediaControlConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlDao
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal class MediaControlRepositoryImpl @Inject constructor(
    private val dao: MediaControlDao,
    private val serverManager: ServerManager,
) : MediaControlRepository {

    override suspend fun getEntityState(config: MediaControlEntityConfig): MediaControlState? = try {
        serverManager.integrationRepository(config.serverId)
            .getEntity(config.entityId)
            ?.toMediaControlState(serverId = config.serverId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to fetch entity state for ${config.entityId}")
        null
    }

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

    override suspend fun getConfiguredEntities(): List<MediaControlEntityConfig> =
        dao.getAll().map { it.toEntityConfig() }

    override fun observeConfiguredEntities(): Flow<List<MediaControlEntityConfig>> =
        dao.getAllFlow().map { list -> list.map { it.toEntityConfig() } }

    override suspend fun setConfiguredEntities(entities: List<MediaControlEntityConfig>) {
        dao.replaceAll(
            entities.mapIndexed { index, config ->
                MediaControlConfig(
                    serverId = config.serverId,
                    entityId = config.entityId,
                    position = index,
                )
            },
        )
    }
}

private fun MediaControlConfig.toEntityConfig() = MediaControlEntityConfig(
    serverId = serverId,
    entityId = entityId,
)

private fun Entity.toMediaControlState(serverId: Int): MediaControlState {
    val playbackState = when (state) {
        "playing" -> MediaPlaybackState.Playing
        "paused" -> MediaPlaybackState.Paused
        "buffering" -> MediaPlaybackState.Buffering
        "idle", "standby" -> MediaPlaybackState.Idle
        else -> MediaPlaybackState.Off
    }

    val repeatMode = when (attributes["repeat"]?.toString()) {
        "one" -> MediaRepeatMode.One
        "all" -> MediaRepeatMode.All
        else -> MediaRepeatMode.Off
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
        supportsStop = supportsStop(),
        supportsMute = supportsVolumeMute(),
        supportsShuffleSet = supportsShuffleSet(),
        supportsRepeatSet = supportsRepeatSet(),
        volumeLevel = if (supportsVolumeSet()) (attributes["volume_level"] as? Number)?.toFloat() else null,
        isVolumeMuted = getVolumeMuted(),
        shuffle = getShuffle(),
        repeatMode = repeatMode,
        entityFriendlyName = attributes["friendly_name"] as? String,
        albumArtist = getMediaAlbumArtist(),
        mediaContentType = getMediaContentType(),
        mediaTrack = getMediaTrack(),
        mediaChannel = getMediaChannel(),
        mediaSeriesTitle = getMediaSeriesTitle(),
        appName = getAppName(),
    )
}
