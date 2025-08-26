package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonWidgetDao : WidgetDao<ButtonWidgetEntity> {
    @Query("SELECT * FROM button_widgets WHERE id = :id")
    suspend fun get(id: Int): ButtonWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: ButtonWidgetEntity)

    @Query("DELETE FROM button_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM button_widgets WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM button_widgets")
    suspend fun getAll(): List<ButtonWidgetEntity>

    @Query("SELECT * FROM button_widgets")
    fun getAllFlow(): Flow<List<ButtonWidgetEntity>>

    @Query("SELECT COUNT(*) FROM button_widgets")
    override fun getWidgetCountFlow(): Flow<Int>
}
