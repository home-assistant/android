package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GridWidgetDao : WidgetDao {
    @Transaction
    @Query("SELECT * FROM grid_widgets WHERE id = :id")
    abstract fun get(id: Int): GridWidgetWithItemsEntity?

    fun add(gridWidgetWithItemsEntity: GridWidgetWithItemsEntity) {
        addItems(gridWidgetWithItemsEntity.items)
        addGridWidget(gridWidgetWithItemsEntity.gridWidget)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun addGridWidget(items: GridWidgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun addItems(items: List<GridWidgetItemEntity>)

    @Query("DELETE FROM grid_widgets WHERE id = :id")
    abstract override suspend fun delete(id: Int)

    @Query("DELETE FROM grid_widgets WHERE id IN (:ids)")
    abstract suspend fun deleteAll(ids: IntArray)

    @Transaction
    @Query("SELECT * FROM grid_widgets")
    abstract suspend fun getAll(): List<GridWidgetWithItemsEntity>

    @Transaction
    @Query("SELECT * FROM grid_widgets")
    abstract fun observeAll(): Flow<List<GridWidgetWithItemsEntity>>
}
