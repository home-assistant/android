package io.homeassistant.companion.android.database.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPlayerControlsWidgetDao : WidgetDao {

    @Query("SELECT * FROM mediaplayctrls_widgets WHERE id = :id")
    fun get(id: Int): MediaPlayerControlsWidgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(mediaPlayCtrlWidgetEntity: MediaPlayerControlsWidgetEntity)

    @Query("DELETE FROM mediaplayctrls_widgets WHERE id = :id")
    override suspend fun delete(id: Int)

    @Query("DELETE FROM mediaplayctrls_widgets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: IntArray)

    @Query("SELECT * FROM mediaplayctrls_widgets")
    suspend fun getAll(): List<MediaPlayerControlsWidgetEntity>

    @Query("SELECT * FROM mediaplayctrls_widgets")
    fun getAllFlow(): Flow<List<MediaPlayerControlsWidgetEntity>>
}
