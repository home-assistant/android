package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CachesDao {

    @Query("SELECT * FROM cache where id = :id LIMIT 1")
    fun get(id: String): Caches

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(cache: Caches)

    @Query("DELETE FROM cache where id = :id")
    fun delete(id: String)

    @Query("DELETE FROM cache")
    fun deleteAll()

}
