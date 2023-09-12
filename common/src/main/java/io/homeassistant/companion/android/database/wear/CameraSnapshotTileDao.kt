package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CameraSnapshotTileDao {

    @Query("SELECT * FROM camera_snapshot_tiles WHERE id = :id")
    suspend fun get(id: Int): CameraSnapshotTile?

    @Query("SELECT * FROM camera_snapshot_tiles ORDER BY id ASC")
    fun getAll(): List<CameraSnapshotTile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tile: CameraSnapshotTile)

    @Query("DELETE FROM camera_snapshot_tiles where id = :id")
    fun delete(id: Int)
}
