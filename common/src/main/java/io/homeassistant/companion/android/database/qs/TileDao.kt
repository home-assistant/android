package io.homeassistant.companion.android.database.qs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TileDao {

    @Query("SELECT * FROM qs_tiles WHERE tileId = :tileId")
    suspend fun get(tileId: String): TileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tileEntity: TileEntity)
}
