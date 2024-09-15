package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class ButtonWidgetRepositoryImpl @Inject constructor(
    private val dao: ButtonWidgetDao
) : ButtonWidgetRepository {

    override fun get(id: Int): ButtonWidgetEntity? {
        return dao.get(id)
    }

    override suspend fun add(entity: ButtonWidgetEntity) = dao.add(entity)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<ButtonWidgetEntity> = dao.getAll()

    override suspend fun getAllFlow(): Flow<List<ButtonWidgetEntity>> = dao.getAllFlow().flowOn(Dispatchers.IO)

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String) = error("Not Implemented")
}
