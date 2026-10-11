package io.homeassistant.companion.android.database.widget

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoWidgetDao : WidgetDao<TodoWidgetEntity> {

    @Query("SELECT * FROM todo_widget WHERE id = :id")
    suspend fun get(id: Int): TodoWidgetEntity?

    @Query("SELECT * FROM todo_widget WHERE id = :id")
    fun getFlow(id: Int): Flow<TodoWidgetEntity?>

    @Query("SELECT * FROM todo_widget")
    suspend fun getAll(): List<TodoWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: TodoWidgetEntity)

    @Query("DELETE FROM todo_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM todo_widget WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM todo_widget")
    fun getAllFlow(): Flow<List<TodoWidgetEntity>>

    @Query("UPDATE todo_widget SET latest_update_data = :lastUpdateData WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdateData: TodoWidgetEntity.LastUpdateData)

    @Query("SELECT COUNT(*) FROM todo_widget")
    override fun getWidgetCountFlow(): Flow<Int>
}
