package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.widget.converters.GridWidgetItemConverter
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(GridWidgetItemConverter::class)
interface GridWidgetDao : WidgetDao<GridWidgetEntity> {

    @Query("SELECT * FROM grid_widgets WHERE id = :id")
    suspend fun get(id: Int): GridWidgetEntity?

    @Query("SELECT * FROM grid_widgets WHERE id = :id")
    fun observe(id: Int): Flow<GridWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: GridWidgetEntity)

    @Query("DELETE FROM grid_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM grid_widgets WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM grid_widgets")
    suspend fun getAll(): List<GridWidgetEntity>

    @Query("SELECT * FROM grid_widgets")
    fun observeAll(): Flow<List<GridWidgetEntity>>

    @Query("SELECT COUNT(*) FROM grid_widgets")
    override fun getWidgetCountFlow(): Flow<Int>
}
