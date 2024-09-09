package io.homeassistant.companion.android.database.widget.graph

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
    fun getOrderedHistories(): List<GraphWidgetHistoryEntity>? {
        return histories?.sortedBy { it.lastChanged }
    }
}
