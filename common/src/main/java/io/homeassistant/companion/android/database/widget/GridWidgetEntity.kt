package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "grid_widgets")
data class GridWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "require_authentication", defaultValue = "0")
    val requireAuthentication: Boolean
) : WidgetEntity

data class GridWidgetWithItemsEntity(
    @Embedded val gridWidget: GridWidgetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "grid_id"
    )
    val items: List<GridWidgetItemEntity>
)
