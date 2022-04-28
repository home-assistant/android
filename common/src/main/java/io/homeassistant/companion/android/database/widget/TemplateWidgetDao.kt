package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateWidgetDao : WidgetDao {

    @Query("SELECT * FROM template_widgets WHERE id = :id")
    fun get(id: Int): TemplateWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(templateWidgetEntity: TemplateWidgetEntity)

    @Query("DELETE FROM template_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM template_widgets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM template_widgets")
    suspend fun getAll(): List<TemplateWidgetEntity>

    @Query("SELECT * FROM template_widgets")
    fun getAllFlow(): Flow<List<TemplateWidgetEntity>>

    @Query("UPDATE template_widgets SET last_update = :lastUpdate WHERE id = :widgetId")
    suspend fun updateTemplateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
