package io.homeassistant.companion.android.repositories

import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class StaticWidgetRepositoryImpl @Inject constructor(
    private val dao: StaticWidgetDao
) : StaticWidgetRepository {

    override fun get(id: Int): StaticWidgetEntity? = dao.get(id)

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String) = dao.updateWidgetLastUpdate(widgetId, lastUpdate)

    override suspend fun add(entity: StaticWidgetEntity) = dao.add(entity)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<StaticWidgetEntity> = dao.getAll()

    override suspend fun getAllFlow(): Flow<List<StaticWidgetEntity>> = dao.getAllFlow().flowOn(Dispatchers.IO)
}
