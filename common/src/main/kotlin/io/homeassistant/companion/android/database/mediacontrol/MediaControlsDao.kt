package io.homeassistant.companion.android.database.mediacontrol

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
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
