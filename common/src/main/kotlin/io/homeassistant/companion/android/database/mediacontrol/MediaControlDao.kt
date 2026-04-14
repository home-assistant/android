package io.homeassistant.companion.android.database.mediacontrol

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaControlDao {

    @Query("SELECT * FROM media_control_entity_config ORDER BY `index` ASC")
    fun getAllFlow(): Flow<List<MediaControlConfig>>

    @Query("SELECT * FROM media_control_entity_config ORDER BY `index` ASC")
    suspend fun getAll(): List<MediaControlConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaControlConfig>)

    @Query("DELETE FROM media_control_entity_config")
    suspend fun deleteAll()

    @Query("DELETE FROM media_control_entity_config WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Int)

    @Transaction
    suspend fun replaceAll(entities: List<MediaControlConfig>) {
        deleteAll()
        insertAll(entities)
    }
}
