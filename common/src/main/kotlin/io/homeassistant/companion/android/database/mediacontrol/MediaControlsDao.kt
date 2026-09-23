package io.homeassistant.companion.android.database.mediacontrol

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaControlsDao {

    @Query("SELECT * FROM media_controls_entity_config")
    fun getAllFlow(): Flow<List<MediaControlsConfig>>

    @Query("SELECT * FROM media_controls_entity_config")
    suspend fun getAll(): List<MediaControlsConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: MediaControlsConfig)

    @Delete
    suspend fun delete(config: MediaControlsConfig)

    @Query("DELETE FROM media_controls_entity_config WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Int)
}
