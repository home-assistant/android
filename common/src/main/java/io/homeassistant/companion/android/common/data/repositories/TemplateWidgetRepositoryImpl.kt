package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class TemplateWidgetRepositoryImpl @Inject constructor(
    private val dao: TemplateWidgetDao
) : TemplateWidgetRepository {

    override fun get(id: Int): TemplateWidgetEntity? = dao.get(id)

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = dao.deleteAll(ids)

    override suspend fun getAll(): List<TemplateWidgetEntity> = dao.getAll()

    override suspend fun getAllFlow(): Flow<List<TemplateWidgetEntity>> = dao.getAllFlow().flowOn(Dispatchers.IO)

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String) = dao.updateTemplateWidgetLastUpdate(widgetId, lastUpdate)

    override suspend fun add(entity: TemplateWidgetEntity) = dao.add(entity)
}
