package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.widget.converters.TodoLastUpdateDataConverter
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(TodoLastUpdateDataConverter::class)
interface TodoWidgetDao : WidgetDao {

    @Query("SELECT * FROM todo_widget WHERE id = :id")
    suspend fun get(id: Int): TodoWidgetEntity?

    @Query("SELECT * FROM todo_widget")
    suspend fun getAll(): List<TodoWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(todoWidgetEntity: TodoWidgetEntity)

    @Query("DELETE FROM todo_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM todo_widget WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM todo_widget")
    fun getAllFlow(): Flow<List<TodoWidgetEntity>>

    @Query("UPDATE todo_widget SET latest_update_data = :lastUpdateData WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdateData: TodoWidgetEntity.LastUpdateData)
}
