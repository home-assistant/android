package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryWidgetDao : WidgetDao {

    @Query("SELECT * FROM history_widgets WHERE id = :id")
    fun get(id: Int): HistoryWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(historyWidgetEntity: HistoryWidgetEntity)

    @Query("DELETE FROM history_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM history_widgets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM history_widgets")
    suspend fun getAll(): List<HistoryWidgetEntity>

    @Query("SELECT * FROM history_widgets")
    fun getAllFlow(): Flow<List<HistoryWidgetEntity>>

    @Query("UPDATE history_widgets SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
