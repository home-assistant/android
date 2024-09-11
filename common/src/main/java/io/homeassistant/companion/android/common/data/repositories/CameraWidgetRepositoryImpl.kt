package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class CameraWidgetRepositoryImpl @Inject constructor(
    private val dao: CameraWidgetDao
) : CameraWidgetRepository {

    override suspend fun add(entity: CameraWidgetEntity) = dao.add(entity)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<CameraWidgetEntity> = dao.getAll()

    override fun getAllFlow(): Flow<List<CameraWidgetEntity>> = dao.getAllFlow().flowOn(Dispatchers.IO)

    override fun get(id: Int): CameraWidgetEntity? = dao.get(id)
}
