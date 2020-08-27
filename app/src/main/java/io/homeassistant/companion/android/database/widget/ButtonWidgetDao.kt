package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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
}
