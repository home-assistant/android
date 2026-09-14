package io.homeassistant.companion.android.common.data.mediacontrol

import io.homeassistant.companion.android.database.mediacontrol.MediaControlConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class MediaControlRepositoryImpl @Inject constructor(private val dao: MediaControlDao) :
    MediaControlRepository {

    override suspend fun getConfiguredEntities(): List<MediaControlEntityConfig> =
        dao.getAll().map { it.toEntityConfig() }

    override fun observeConfiguredEntities(): Flow<List<MediaControlEntityConfig>> =
        dao.getAllFlow().map { list -> list.map { it.toEntityConfig() } }

    override suspend fun addConfiguredEntity(entity: MediaControlEntityConfig) {
        dao.insert(MediaControlConfig(entity.serverId, entity.entityId))
    }

    override suspend fun removeConfiguredEntity(entity: MediaControlEntityConfig) {
        dao.delete(MediaControlConfig(entity.serverId, entity.entityId))
    }
}

private fun MediaControlConfig.toEntityConfig() = MediaControlEntityConfig(
    serverId = serverId,
    entityId = entityId,
)
