package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories

interface GraphWidgetRepository : BaseDaoWidgetRepository<GraphWidgetEntity> {

    suspend fun getGraphWidgetWithHistories(id: Int): GraphWidgetWithHistories?

    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTimeInHours: Int)

    fun deleteEntries(appWidgetId: Int)

    suspend fun insertGraphWidgetHistory(historyEntityList: List<GraphWidgetHistoryEntity>)

    suspend fun updateWidgetData(
        appWidgetId: Int,
        labelText: String? = null,
        timeRange: Int? = null,
        unitOfMeasurement: String? = null,
        entityId: String? = null,
        smoothGraphs: Boolean? = null,
        lastUpdate: Long? = null
    )

    suspend fun checkIfExceedsAverageInterval(widgetId: Int, lastChangedToCompare: Long): Boolean
}
