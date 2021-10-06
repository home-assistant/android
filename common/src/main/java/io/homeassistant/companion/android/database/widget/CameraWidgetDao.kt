package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CameraWidgetDao {

    @Query("SELECT * FROM camera_widgets WHERE id = :id")
    fun get(id: Int): CameraWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(cameraWidgetEntity: CameraWidgetEntity)

    @Update
    fun update(cameraWidgetEntity: CameraWidgetEntity)

    @Query("DELETE FROM camera_widgets WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT * FROM camera_widgets")
    fun getAll(): Array<CameraWidgetEntity>?
}
