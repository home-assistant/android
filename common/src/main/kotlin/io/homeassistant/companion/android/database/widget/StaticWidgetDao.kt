package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StaticWidgetDao : WidgetDao {

    @Query("SELECT * FROM static_widget WHERE id = :id")
    fun get(id: Int): StaticWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(staticWidgetEntity: StaticWidgetEntity)

    @Query("DELETE FROM static_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM static_widget WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM static_widget")
    suspend fun getAll(): List<StaticWidgetEntity>

    @Query("SELECT * FROM static_widget")
    fun getAllFlow(): Flow<List<StaticWidgetEntity>>

    @Query("UPDATE static_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
