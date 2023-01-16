package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteCachesDao {

    @Query("SELECT * FROM favorite_cache where id = :id")
    fun get(id: String): FavoriteCaches?

    @Query("SELECT * FROM favorite_cache ORDER BY id ASC")
    fun getAll(): List<FavoriteCaches>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(cache: FavoriteCaches)

    @Query("DELETE FROM favorite_cache where id = :id")
    fun delete(id: String)

    @Query("DELETE FROM favorite_cache")
    fun deleteAll()
}
