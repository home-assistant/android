package io.homeassistant.companion.android.repositories

import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class CameraWidgetRepositoryImpl @Inject constructor(
    private val dao: CameraWidgetDao
) : CameraWidgetRepository {

    override suspend fun add(entity: CameraWidgetEntity) = dao.add(entity)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<CameraWidgetEntity> = dao.getAll()

    override suspend fun getAllFlow(): Flow<List<CameraWidgetEntity>> = dao.getAllFlow()

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String) = error("Not Implemented")

    override fun get(id: Int): CameraWidgetEntity? = dao.get(id)
}
