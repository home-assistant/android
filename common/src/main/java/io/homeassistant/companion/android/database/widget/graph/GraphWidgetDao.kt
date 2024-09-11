package io.homeassistant.companion.android.database.widget.graph

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.homeassistant.companion.android.database.widget.WidgetDao
import kotlinx.coroutines.flow.Flow

@Dao
interface GraphWidgetDao : WidgetDao {

    @Query("SELECT * FROM graph_widget WHERE id = :id")
    fun get(id: Int): GraphWidgetEntity?

    @Transaction
    @Query("SELECT * FROM graph_widget WHERE id = :id")
    suspend fun getWithHistories(id: Int): GraphWidgetWithHistories?

    @Query("SELECT last_changed FROM graph_widget_history WHERE graph_widget_id = :widgetId ORDER BY last_changed")
    suspend fun getLastChangedTimesForWidget(widgetId: Int): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(graphWidgetEntity: GraphWidgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(graphWidgetEntities: List<GraphWidgetHistoryEntity>)

    @Transaction
    suspend fun insertAllInTransaction(entities: List<GraphWidgetHistoryEntity>) {
        addAll(entities)
    }

    @Transaction
    suspend fun updateWidgetInTransaction(
        appWidgetId: Int,
        labelText: String? = null,
        timeRange: Int? = null,
        unitOfMeasurement: String? = null,
        entityId: String? = null,
        smoothGraphs: Boolean? = null,
        significantChangesOnly: Boolean? = null,
        lastUpdate: Long? = null
    ) {
        labelText?.let { updateWidgetLabel(appWidgetId, it) }
        timeRange?.let { updateWidgetTimeRange(appWidgetId, it) }
        unitOfMeasurement?.let { updateWidgetSensorUnitOfMeasurement(appWidgetId, it) }
        entityId?.let { updateWidgetSensorEntityId(appWidgetId, it) }
        smoothGraphs?.let { updateWidgetSmoothGraphs(appWidgetId, it) }
        lastUpdate?.let { updateWidgetLastUpdate(appWidgetId, it) }
    }

    // Delete a specific GraphWidgetEntity by id
    @Query("DELETE FROM graph_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM graph_widget WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM graph_widget")
    suspend fun getAll(): List<GraphWidgetEntity>

    @Query("SELECT * FROM graph_widget")
    fun getAllFlow(): Flow<List<GraphWidgetEntity>>

    @Query("UPDATE graph_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: Long)

    @Query("UPDATE graph_widget SET label = :label WHERE id = :widgetId")
    fun updateWidgetLabel(widgetId: Int, label: String)

    @Query("UPDATE graph_widget SET smoothGraphs = :smoothGraphs WHERE id = :widgetId")
    fun updateWidgetSmoothGraphs(widgetId: Int, smoothGraphs: Boolean)

    @Query("UPDATE graph_widget SET graph_time_range = :timeRange WHERE id = :widgetId")
    fun updateWidgetTimeRange(widgetId: Int, timeRange: Int)

    @Query("UPDATE graph_widget SET entity_id = :entityId WHERE id = :widgetId")
    fun updateWidgetSensorEntityId(widgetId: Int, entityId: String)

    @Query("UPDATE graph_widget SET unit_of_measurement = :unitOfMeasurement WHERE id = :widgetId")
    fun updateWidgetSensorUnitOfMeasurement(widgetId: Int, unitOfMeasurement: String)

    @Query("DELETE FROM graph_widget_history WHERE graph_widget_id = :widgetId ")
    fun deleteAllEntries(widgetId: Int)

    @Query("DELETE FROM graph_widget_history WHERE graph_widget_id = :appWidgetId AND last_changed <= :cutoffTime")
    suspend fun deleteEntriesOlderThan(appWidgetId: Int, cutoffTime: Long): Int
}
