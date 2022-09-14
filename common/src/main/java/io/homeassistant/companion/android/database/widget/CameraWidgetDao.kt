package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraWidgetDao : WidgetDao {

    @Query("SELECT * FROM camera_widgets WHERE id = :id")
    fun get(id: Int): CameraWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(cameraWidgetEntity: CameraWidgetEntity)

    @Query("DELETE FROM camera_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM camera_widgets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM camera_widgets")
    suspend fun getAll(): List<CameraWidgetEntity>

    @Query("SELECT * FROM camera_widgets")
    fun getAllFlow(): Flow<List<CameraWidgetEntity>>
}
