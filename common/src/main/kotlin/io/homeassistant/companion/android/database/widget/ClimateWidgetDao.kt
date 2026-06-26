package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.widget.converters.ClimateLastUpdateDataConverter
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(ClimateLastUpdateDataConverter::class)
interface ClimateWidgetDao : WidgetDao<ClimateWidgetEntity> {

    @Query("SELECT * FROM climate_widget WHERE id = :id")
    suspend fun get(id: Int): ClimateWidgetEntity?

    @Query("SELECT * FROM climate_widget WHERE id = :id")
    fun getFlow(id: Int): Flow<ClimateWidgetEntity?>

    @Query("SELECT * FROM climate_widget")
    suspend fun getAll(): List<ClimateWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: ClimateWidgetEntity)

    @Query("DELETE FROM climate_widget WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM climate_widget WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM climate_widget")
    fun getAllFlow(): Flow<List<ClimateWidgetEntity>>

    @Query("UPDATE climate_widget SET latest_update_data = :lastUpdateData WHERE id = :widgetId")
    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdateData: ClimateWidgetEntity.LastUpdateData)

    @Query("SELECT COUNT(*) FROM climate_widget")
    override fun getWidgetCountFlow(): Flow<Int>
}
