package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.graph.GraphWidgetDao
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class GraphWidgetRepositoryImpl @Inject constructor(
    private val graphWidgetDao: GraphWidgetDao
) : GraphWidgetRepository {

    override fun get(id: Int): GraphWidgetEntity? = graphWidgetDao.get(id)

    override suspend fun getGraphWidgetWithHistories(id: Int): GraphWidgetWithHistories? = graphWidgetDao.getWithHistories(id)

    override suspend fun add(entity: GraphWidgetEntity) {
        if (graphWidgetDao.get(entity.id) == null) {
            graphWidgetDao.add(entity)
        }
    }

    override suspend fun delete(id: Int) = graphWidgetDao.delete(id)

    override suspend fun deleteAll(ids: IntArray) = graphWidgetDao.deleteAll(ids)

    override suspend fun getAll(): List<GraphWidgetEntity> = graphWidgetDao.getAll()

    override fun getAllFlow(): Flow<List<GraphWidgetEntity>> = graphWidgetDao.getAllFlow().flowOn(Dispatchers.IO)

    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: Long) = graphWidgetDao.updateWidgetLastUpdate(widgetId, lastUpdate)

    override suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTimeInHours: Int) {
        graphWidgetDao.deleteEntriesOlderThan(appWidgetId, System.currentTimeMillis() - (60 * 60 * 1000 * cutoffTimeInHours))
    }

    override fun deleteEntries(appWidgetId: Int) {
        graphWidgetDao.deleteAllEntries(appWidgetId)
    }

    override suspend fun insertGraphWidgetHistory(historyEntityList: List<GraphWidgetHistoryEntity>) {
        graphWidgetDao.insertAllInTransaction(historyEntityList)
    }

    override suspend fun checkIfExceedsAverageInterval(widgetId: Int, lastChangedToCompare: Long): Boolean {
        val lastChangedList = graphWidgetDao.getLastChangedTimesForWidget(widgetId)

        if (lastChangedList.size < 2) return true

        val intervals = lastChangedList.zipWithNext { a, b -> b - a }
        val averageInterval = intervals.average()

        val maxInterval = intervals.maxOrNull() ?: averageInterval
        val minInterval = intervals.minOrNull() ?: averageInterval
        val proximityMultiplier = if (maxInterval != 0L) (maxInterval.toDouble().div(minInterval.toDouble())) else 1.0

        val adjustedMultiplier = when {
            proximityMultiplier < 1.5 -> 2.5
            proximityMultiplier < 2.0 -> 2.75
            else -> 3.0
        }

        val lastChanged = lastChangedList.last()
        return (lastChangedToCompare - lastChanged) > averageInterval * adjustedMultiplier
    }

    override suspend fun updateWidgetData(
        appWidgetId: Int,
        labelText: String?,
        timeRange: Int?,
        unitOfMeasurement: String?,
        entityId: String?,
        smoothGraphs: Boolean?,
        lastUpdate: Long?
    ) {
        graphWidgetDao.updateWidgetInTransaction(
            appWidgetId = appWidgetId,
            unitOfMeasurement = unitOfMeasurement,
            entityId = entityId,
            labelText = labelText,
            timeRange = timeRange,
            smoothGraphs = smoothGraphs,
            lastUpdate = lastUpdate
        )
    }
}
