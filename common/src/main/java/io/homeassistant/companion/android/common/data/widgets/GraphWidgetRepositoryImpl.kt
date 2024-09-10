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

    override suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: Long) {
        graphWidgetDao.updateWidgetLastUpdate(widgetId, lastUpdate)
    }

    override suspend fun add(entity: GraphWidgetEntity) {
        if (graphWidgetDao.get(entity.id) == null) {
            graphWidgetDao.add(entity)
        }
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

    override suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTimeInHours: Int) {
        graphWidgetDao.deleteEntriesOlderThan(appWidgetId, System.currentTimeMillis() - (60 * 60 * 1000 * cutoffTimeInHours))
    }

    override fun deleteEntries(appWidgetId: Int) {
        graphWidgetDao.deleteAllEntries(appWidgetId)
    }

    override suspend fun insertGraphWidgetHistory(historyEntityList: List<GraphWidgetHistoryEntity>) {
        graphWidgetDao.insertAllInTransaction(historyEntityList)
    }

    override fun updateWidgetLastLabel(appWidgetId: Int, labelText: String) {
        graphWidgetDao.updateWidgetLabel(appWidgetId, labelText)
    }

    override fun updateWidgetTimeRange(appWidgetId: Int, timeRange: Int) {
        graphWidgetDao.updateWidgetTimeRange(appWidgetId, timeRange)
    }

    override fun updateWidgetSensorUnitOfMeasurement(appWidgetId: Int, unitOfMeasurement: String) {
        graphWidgetDao.updateWidgetSensorUnitOfMeasurement(appWidgetId, unitOfMeasurement)
    }

    override fun updateWidgetSensorEntityId(appWidgetId: Int, entityId: String) {
        graphWidgetDao.updateWidgetSensorEntityId(appWidgetId, entityId)
    }

    override suspend fun checkIfExceedsAverageInterval(widgetId: Int, lastChangedToCompare: Long): Boolean {
        val lastChangedList = graphWidgetDao.getLastChangedTimesForWidget(widgetId)

        if (lastChangedList.size < 2) return false

        val averageInterval = lastChangedList
            .zipWithNext { a, b -> b - a }
            .average()

        val lastChanged = lastChangedList.last()

        return (lastChangedToCompare - lastChanged) < averageInterval
    }
}
