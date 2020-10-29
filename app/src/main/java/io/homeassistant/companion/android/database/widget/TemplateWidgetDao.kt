package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TemplateWidgetDao {

    @Query("SELECT * FROM template_widgets WHERE id = :id")
    fun get(id: Int): TemplateWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(templateWidgetEntity: TemplateWidgetEntity)

    @Update
    fun update(templateWidgetEntity: TemplateWidgetEntity)

    @Query("DELETE FROM template_widgets WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT * FROM template_widgets")
    fun getAll(): Array<TemplateWidgetEntity>?
}
