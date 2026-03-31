package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StaticWidgetDao : WidgetDao<StaticWidgetEntity> {

    @Query("SELECT * FROM static_widget WHERE id = :id")
    suspend fun get(id: Int): StaticWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: StaticWidgetEntity)

    @Query("DELETE FROM static_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM static_widget WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM static_widget")
    suspend fun getAll(): List<StaticWidgetEntity>

    @Query("SELECT * FROM static_widget")
    fun getAllFlow(): Flow<List<StaticWidgetEntity>>

    @Query("UPDATE static_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)

    @Query("SELECT COUNT(*) FROM static_widget")
    override fun getWidgetCountFlow(): Flow<Int>
}
