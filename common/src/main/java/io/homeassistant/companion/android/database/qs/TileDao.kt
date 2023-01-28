package io.homeassistant.companion.android.database.qs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TileDao {

    @Query("SELECT * FROM qs_tiles WHERE tile_id = :tileId")
    suspend fun get(tileId: String): TileEntity?

    @Query("SELECT * FROM qs_tiles")
    suspend fun getAll(): List<TileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tileEntity: TileEntity)
}

suspend fun TileDao.getHighestInUse(): TileEntity? {
    return getAll()
        .filter { it.added || it.isSetup }
        .sortedByDescending { it.tileId.split("_")[1].toInt() }
        .getOrNull(0)
}
