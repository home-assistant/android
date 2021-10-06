package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MediaPlayerControlsWidgetDao {

    @Query("SELECT * FROM mediaplayctrls_widgets WHERE id = :id")
    fun get(id: Int): MediaPlayerControlsWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(mediaPlayCtrlWidgetEntity: MediaPlayerControlsWidgetEntity)

    @Update
    fun update(mediaPlayCtrlWidgetEntity: MediaPlayerControlsWidgetEntity)

    @Query("DELETE FROM mediaplayctrls_widgets WHERE id = :id")
    fun delete(id: Int)

    @Query("SELECT * FROM mediaplayctrls_widgets")
    fun getAll(): Array<MediaPlayerControlsWidgetEntity>?
}
