package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class MediaPlayerControlsWidgetRepositoryImpl @Inject constructor(
    private val dao: MediaPlayerControlsWidgetDao
) : MediaPlayerControlsWidgetRepository {

    override fun get(id: Int): MediaPlayerControlsWidgetEntity? = dao.get(id)

    override suspend fun add(entity: MediaPlayerControlsWidgetEntity) = dao.add(entity)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<MediaPlayerControlsWidgetEntity> = dao.getAll()

    override fun getAllFlow(): Flow<List<MediaPlayerControlsWidgetEntity>> = dao.getAllFlow().flowOn(Dispatchers.IO)
}
