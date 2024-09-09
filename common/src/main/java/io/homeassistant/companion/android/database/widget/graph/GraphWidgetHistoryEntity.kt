package io.homeassistant.companion.android.database.widget.graph

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "graph_widget_history",
    foreignKeys = [
        ForeignKey(
            entity = GraphWidgetEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("graph_widget_id"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("last_changed"),
        Index("graph_widget_id")
    ]
)
data class GraphWidgetHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "graph_widget_id")
    val graphWidgetId: Int,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "last_changed")
    var lastChanged: Long
)
