package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StaticWidgetDao {

    @Query("SELECT * FROM static_widget WHERE id = :id")
    fun get(id: Int): StaticWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(staticWidgetEntity: StaticWidgetEntity)

    @Update
    fun update(staticWidgetEntity: StaticWidgetEntity)

    @Query("DELETE FROM static_widget WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT * FROM static_widget")
    fun getAll(): List<StaticWidgetEntity>?

    @Query("SELECT * FROM static_widget")
    fun getAllFlow(): Flow<List<StaticWidgetEntity>>?

    @Query("UPDATE static_widget SET last_update = :lastUpdate WHERE id = :widgetId")
    fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
