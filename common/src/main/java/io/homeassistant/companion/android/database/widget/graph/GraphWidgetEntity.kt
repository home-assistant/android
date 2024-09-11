package io.homeassistant.companion.android.database.widget.graph

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.homeassistant.companion.android.database.widget.ThemeableWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetEntity

@Entity(tableName = "graph_widget")
data class GraphWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "entity_id")
    override val entityId: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "unit_of_measurement")
    val unitOfMeasurement: String,
    @ColumnInfo(name = "graph_time_range")
    val timeRange: Int = 24,
    @ColumnInfo(name = "last_update")
    val lastUpdate: Long,
    @ColumnInfo(name = "smooth_graphs")
    val smoothGraphs: Boolean,
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    override val textColor: String? = null
) : WidgetEntity, ThemeableWidgetEntity
