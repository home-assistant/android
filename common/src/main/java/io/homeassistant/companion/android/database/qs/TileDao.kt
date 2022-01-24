package io.homeassistant.companion.android.database.qs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TileDao {

    @Query("SELECT * FROM qs_tiles WHERE tileId = :tileId")
    fun get(tileId: String): TileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(tileEntity: TileEntity)

    @Update
    fun update(tileEntity: TileEntity)

    @Query("DELETE FROM qs_tiles WHERE tileId = :id")
    fun delete(id: String)

    @Query("SELECT * FROM qs_tiles")
    fun getAll(): List<TileEntity>?

    @Query("SELECT * FROM qs_tiles")
    fun getAllFlow(): Flow<List<TileEntity>>?
}
