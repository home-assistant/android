package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPlayerControlsWidgetDao : WidgetDao {

    @Query("SELECT * FROM media_player_controls_widgets WHERE id = :id")
    fun get(id: Int): MediaPlayerControlsWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(mediaPlayCtrlWidgetEntity: MediaPlayerControlsWidgetEntity)

    @Query("DELETE FROM media_player_controls_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM media_player_controls_widgets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM media_player_controls_widgets")
    suspend fun getAll(): List<MediaPlayerControlsWidgetEntity>

    @Query("SELECT * FROM media_player_controls_widgets")
    fun getAllFlow(): Flow<List<MediaPlayerControlsWidgetEntity>>
}
