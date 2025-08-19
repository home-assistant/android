package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPlayerControlsWidgetDao : WidgetDao<MediaPlayerControlsWidgetEntity> {
    @Query("SELECT * FROM media_player_controls_widgets WHERE id = :id")
    suspend fun get(id: Int): MediaPlayerControlsWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun add(entity: MediaPlayerControlsWidgetEntity)

    @Query("DELETE FROM media_player_controls_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM media_player_controls_widgets WHERE id IN (:ids)")
    override suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM media_player_controls_widgets")
    suspend fun getAll(): List<MediaPlayerControlsWidgetEntity>

    @Query("SELECT * FROM media_player_controls_widgets")
    fun getAllFlow(): Flow<List<MediaPlayerControlsWidgetEntity>>

    @Query("SELECT COUNT(*) FROM media_player_controls_widgets")
    override fun getWidgetCountFlow(): Flow<Int>
}
