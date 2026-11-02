package io.homeassistant.companion.android.database.location

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
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
