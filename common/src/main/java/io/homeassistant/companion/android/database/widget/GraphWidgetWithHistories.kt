package io.homeassistant.companion.android.database.widget

import androidx.room.Embedded
import androidx.room.Relation

data class GraphWidgetWithHistories(
    @Embedded val graphWidget: GraphWidgetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "graph_widget_id"
    )
    val histories: List<GraphWidgetHistoryEntity>?
) {
    fun getOrderedHistories(startTime: Long? = null, endTime: Long? = null): List<GraphWidgetHistoryEntity>? {
        return histories
            ?.filter { history ->
                (startTime == null || history.sentState >= startTime) &&
                    (endTime == null || history.sentState <= endTime)
            }
            ?.sortedBy { it.sentState }
    }
}
