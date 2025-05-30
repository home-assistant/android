package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.homeassistant.companion.android.database.widget.converters.TodoLastUpdateDataConverter

@TypeConverters(TodoLastUpdateDataConverter::class)
@Entity(tableName = "todo_widget")
data class TodoWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
    @ColumnInfo(name = "show_completed", defaultValue = "true")
    val showCompleted: Boolean = true,
    @ColumnInfo(name = "latest_update_data") val latestUpdateData: LastUpdateData? = null,
) : WidgetEntity, ThemeableWidgetEntity {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LastUpdateData(
        val entityName: String?,
        val todos: List<TodoItem>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TodoItem(
        val uid: String?,
        val summary: String?,
        val status: String?,
    )

    fun isSameConfiguration(other: TodoWidgetEntity): Boolean {
        /**
         *  The only field that is not part of the configuration is [latestUpdateData], we make copy of the data classes
         *  without the [latestUpdateData] field so that we can use the equals method generated.
         *  By doing this we make the check more future proof, if a new configuration field is added.
         */
        return other.copy(latestUpdateData = null) == this.copy(latestUpdateData = null)
    }
}
