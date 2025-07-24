package io.homeassistant.companion.android.database.location

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: LocationHistoryItem): Long

    @Query("SELECT * FROM location_history ORDER BY id DESC")
    fun getAll(): PagingSource<Int, LocationHistoryItem>

    @Query("SELECT * FROM location_history WHERE result IN (:results) ORDER BY id DESC")
    fun getAll(results: List<String>): PagingSource<Int, LocationHistoryItem>

    @Query("DELETE FROM location_history WHERE created < :created")
    suspend fun deleteBefore(created: Long)

    @Query("DELETE FROM location_history")
    suspend fun deleteAll()
}
