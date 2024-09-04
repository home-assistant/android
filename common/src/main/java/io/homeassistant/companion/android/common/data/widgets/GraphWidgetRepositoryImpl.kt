package io.homeassistant.companion.android.common.data.widgets

import io.homeassistant.companion.android.database.widget.graph.GraphWidgetDao
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GraphWidgetRepositoryImpl @Inject constructor(
    private val graphWidgetDao: GraphWidgetDao
) : GraphWidgetRepository {

    override fun get(id: Int): GraphWidgetEntity? {
        return graphWidgetDao.get(id)
    }

    override suspend fun getGraphWidgetWithHistories(id: Int): GraphWidgetWithHistories? {
        return graphWidgetDao.getWithHistories(id)
    }

    override suspend fun add(entity: GraphWidgetEntity) {
        graphWidgetDao.add(entity)
    }

    override suspend fun delete(id: Int) {
        graphWidgetDao.delete(id)
    }

    override suspend fun deleteAll(ids: IntArray) {
        graphWidgetDao.deleteAll(ids)
    }

    override suspend fun getAll(): List<GraphWidgetEntity> {
        return graphWidgetDao.getAll()
    }

    override fun getAllFlow(): Flow<List<GraphWidgetEntity>> {
        return graphWidgetDao.getAllFlow().flowOn(Dispatchers.IO)
    }

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String) {
        graphWidgetDao.updateWidgetLastUpdate(widgetId, lastUpdate)
    }

    override suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTime: Long) {
        graphWidgetDao.deleteEntriesOlderThan(appWidgetId, cutoffTime)
    }

    override suspend fun insertGraphWidgetHistory(historyEntity: GraphWidgetHistoryEntity) {
        graphWidgetDao.add(historyEntity)
    }

    override suspend fun getLastGraphWidgetHistory(appWidgetId: Int): GraphWidgetHistoryEntity? {
        return graphWidgetDao.getLastGraphWidgetHistory(appWidgetId)
    }

    // New function to fetch the last two entries
    override suspend fun getLastEntries(appWidgetId: Int): List<GraphWidgetHistoryEntity> {
        return graphWidgetDao.getLastTwoEntries(appWidgetId)
    }
}
