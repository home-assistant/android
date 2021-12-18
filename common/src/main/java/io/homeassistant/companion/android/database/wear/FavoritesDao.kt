package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites where id = :id")
    fun get(id: String): Favorites?

    @Query("SELECT * FROM favorites ORDER BY position ASC")
    fun getAllFlow(): Flow<List<Favorites>>?

    @Query("SELECT * FROM favorites ORDER BY position ASC")
    fun getAll(): List<Favorites>?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(favorite: Favorites)

    @Update
    fun update(favorite: Favorites)

    @Query("DELETE FROM favorites where id = :id")
    fun delete(id: String)

    @Query("DELETE FROM favorites")
    fun deleteAll()
}
