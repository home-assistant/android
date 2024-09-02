package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GraphWidgetDao : WidgetDao {

    @Query("SELECT * FROM graph_widget WHERE id = :id")
    fun get(id: Int): GraphWidgetEntity?

    @Transaction
    @Query("SELECT * FROM graph_widget WHERE id = :id")
    suspend fun getWithHistories(id: Int): GraphWidgetWithHistories?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(graphWidgetEntity: GraphWidgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(graphWidgetEntity: GraphWidgetHistoryEntity)

    // Delete a specific GraphWidgetEntity by id
    @Query("DELETE FROM graph_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    // Delete multiple GraphWidgetEntity by ids
    @Query("DELETE FROM graph_widget WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    // Retrieve all GraphWidgetEntity records
    @Query("SELECT * FROM graph_widget")
    suspend fun getAll(): List<GraphWidgetEntity>

    // Retrieve all GraphWidgetEntity records as a Flow
    @Query("SELECT * FROM graph_widget")
    fun getAllFlow(): Flow<List<GraphWidgetEntity>>

    // Update the last_update field of a specific GraphWidgetEntity
    @Query("UPDATE graph_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)

    @Query("DELETE FROM graph_widget_history WHERE graph_widget_id = :appWidgetId AND sent_state < :cutoffTime")
    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTime: Long)
}