package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraTileDao {

    @Query("SELECT * FROM camera_tiles WHERE id = :id")
    suspend fun get(id: Int): CameraTile?

    @Query("SELECT * FROM camera_tiles ORDER BY id ASC")
    fun getAllFlow(): Flow<List<CameraTile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tile: CameraTile)

    @Query("DELETE FROM camera_tiles where id = :id")
    suspend fun delete(id: Int)
}
