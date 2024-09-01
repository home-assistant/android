package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "graph_widget_history",
    primaryKeys = ["entity_id", "graph_widget_id"],
    foreignKeys = [
        ForeignKey(
            entity = GraphWidgetEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("graph_widget_id"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sent_state")]
)
data class GraphWidgetHistoryEntity(
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "graph_widget_id")
    val graphWidgetId: Int, // This should match the type of GraphWidgetEntity's id
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "sent_state")
    var sentState: Long
)
