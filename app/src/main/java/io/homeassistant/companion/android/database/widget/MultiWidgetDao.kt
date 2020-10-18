package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MultiWidgetDao {

    @Query("SELECT * FROM multi_widgets WHERE id = :id")
    fun get(id: Int): MultiWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(multiWidgetEntity: MultiWidgetEntity)

    @Update
    fun update(multiWidgetEntity: MultiWidgetEntity)

    @Query("DELETE FROM multi_widgets WHERE id = :id")
    fun delete(id: Int)
}
