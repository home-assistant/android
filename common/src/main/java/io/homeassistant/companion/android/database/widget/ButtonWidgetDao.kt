package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonWidgetDao {

    @Query("SELECT * FROM button_widgets WHERE id = :id")
    fun get(id: Int): ButtonWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(buttonWidgetEntity: ButtonWidgetEntity)

    @Update
    fun update(buttonWidgetEntity: ButtonWidgetEntity)

    @Query("DELETE FROM button_widgets WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT * FROM button_widgets")
    fun getAll(): List<ButtonWidgetEntity>?

    @Query("SELECT * FROM button_widgets")
    fun getAllFlow(): Flow<List<ButtonWidgetEntity>>?
}
