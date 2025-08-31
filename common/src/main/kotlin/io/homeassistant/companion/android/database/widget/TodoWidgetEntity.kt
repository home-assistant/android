package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.widget.converters.TodoLastUpdateDataConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
) : WidgetEntity<TodoWidgetEntity>,
    ThemeableWidgetEntity {

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LastUpdateData(
        // For historical reasons the field is not using snake_case
        @JsonNames("entityName")
        val entityName: String? = null,
        val todos: List<TodoItem>,
    ) : java.io.Serializable

    @Serializable
    data class TodoItem(val uid: String? = null, val summary: String? = null, val status: String? = null) :
        java.io.Serializable

    fun isSameConfiguration(other: TodoWidgetEntity): Boolean {
        /**
         *  The only field that is not part of the configuration is [latestUpdateData], we make copy of the data classes
         *  without the [latestUpdateData] field so that we can use the equals method generated.
         *  By doing this we make the check more future proof, if a new configuration field is added.
         */
        return other.copy(latestUpdateData = null) == this.copy(latestUpdateData = null)
    }

    override fun copyWithWidgetId(appWidgetId: Int): TodoWidgetEntity {
        return copy(id = appWidgetId)
    }
}
