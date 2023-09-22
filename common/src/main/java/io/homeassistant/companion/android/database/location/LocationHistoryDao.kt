package io.homeassistant.companion.android.database.location

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(item: LocationHistoryItem): Long

    @Query("SELECT * FROM location_history WHERE id = :id")
    fun get(id: Int): LocationHistoryItem?

    @Query("SELECT * FROM location_history ORDER BY created DESC")
    fun getAll(): PagingSource<Int, LocationHistoryItem>

    @Query("DELETE FROM location_history WHERE created < :created")
    suspend fun deleteBefore(created: Long)

    @Query("DELETE FROM location_history WHERE server_id = :serverId")
    suspend fun deleteForServer(serverId: Int)

    @Query("DELETE FROM location_history")
    suspend fun deleteAll()
}
