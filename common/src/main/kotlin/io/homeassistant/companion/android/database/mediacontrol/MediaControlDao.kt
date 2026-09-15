package io.homeassistant.companion.android.database.mediacontrol

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaControlDao {

    @Query("SELECT * FROM media_control_entity_config")
    fun getAllFlow(): Flow<List<MediaControlConfig>>

    @Query("SELECT * FROM media_control_entity_config")
    suspend fun getAll(): List<MediaControlConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<MediaControlConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: MediaControlConfig)

    @Query("DELETE FROM media_control_entity_config")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(config: MediaControlConfig)

    @Query("DELETE FROM media_control_entity_config WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Int)
}
