package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
    fun getAll(): List<TemplateWidgetEntity>?

    @Query("SELECT * FROM template_widgets")
    fun getAllFlow(): Flow<List<TemplateWidgetEntity>>?

    @Query("UPDATE template_widgets SET last_update = :lastUpdate WHERE id = :widgetId")
    fun updateTemplateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
