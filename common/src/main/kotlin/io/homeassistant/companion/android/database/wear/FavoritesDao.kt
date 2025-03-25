package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites ORDER BY position ASC")
    fun getAllFavoritesFlow(): Flow<List<Favorites>>

    @Query("SELECT * FROM favorites ORDER BY position ASC")
    suspend fun getAllFavorites(): List<Favorites>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorites)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(favorites: List<Favorites>)

    @Query("SELECT position FROM favorites ORDER BY position DESC LIMIT 1")
    suspend fun getLargestPosition(): Int?

    @Query("DELETE FROM favorites where id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()

    /**
     * Delete all favorites and insert new entries.
     */
    @Transaction
    suspend fun replaceAll(favorites: List<Favorites>) {
        deleteAll()
        addAll(favorites)
    }

    /**
     * Insert a new favorite at the end of the list.
     */
    @Transaction
    suspend fun addToEnd(favoriteEntityId: String) {
        val largestPosition = getLargestPosition() ?: 0
        add(Favorites(favoriteEntityId, position = largestPosition + 1))
    }
}

// Hide position from the get/set operations, since its just used for sorting.

fun FavoritesDao.getAllFlow() = getAllFavoritesFlow().map { favorites -> favorites.map { it.id } }

suspend fun FavoritesDao.getAll() = getAllFavorites().map { it.id }

suspend fun FavoritesDao.replaceAll(favoriteEntityIds: List<String>) {
    replaceAll(favoriteEntityIds.mapIndexed { index, entityId -> Favorites(entityId, index) })
}
