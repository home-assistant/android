package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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
}
