package io.homeassistant.companion.android.common.data.widgets

import io.homeassistant.companion.android.common.data.BaseDaoRepository
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetHistoryEntity
import io.homeassistant.companion.android.database.widget.graph.GraphWidgetWithHistories

interface GraphWidgetRepository : BaseDaoRepository<GraphWidgetEntity> {

    suspend fun getGraphWidgetWithHistories(id: Int): GraphWidgetWithHistories?

    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)

    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTime1: Int, cutoffTime: Long)

    suspend fun insertGraphWidgetHistory(historyEntity: GraphWidgetHistoryEntity)

    fun updateWidgetLastLabel(appWidgetId: Int, labelText: String)

    fun updateWidgetTimeRange(appWidgetId: Int, timeRange: Int)
}
