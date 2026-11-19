package io.homeassistant.companion.android.common.data.mediacontrols

import io.homeassistant.companion.android.database.mediacontrol.MediaControlsConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlsDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class MediaControlsRepositoryImpl @Inject constructor(private val dao: MediaControlsDao) :
    MediaControlsRepository {

    override suspend fun getEntities(): List<MediaControlsEntityConfig> = dao.getAll().map { it.toEntityConfig() }

    override fun observeEntities(): Flow<List<MediaControlsEntityConfig>> =
        dao.getAllFlow().map { list -> list.map { it.toEntityConfig() } }

    override suspend fun addEntity(entity: MediaControlsEntityConfig) {
        dao.insert(MediaControlsConfig(entity.serverId, entity.entityId))
    }

    override suspend fun removeEntity(entity: MediaControlsEntityConfig) {
        dao.delete(MediaControlsConfig(entity.serverId, entity.entityId))
    }
}

private fun MediaControlsConfig.toEntityConfig() = MediaControlsEntityConfig(
    serverId = serverId,
    entityId = entityId,
)
