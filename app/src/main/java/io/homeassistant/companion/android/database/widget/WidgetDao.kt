package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WidgetDao {

    @Query("SELECT * FROM widgets WHERE id = :id")
    fun get(id: Int): Widget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(widget: Widget)

    @Update
    fun update(widget: Widget)

    @Query("DELETE FROM widgets WHERE id = :id")
    fun delete(id: Int)
}
