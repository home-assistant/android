package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EntityStateComplicationsDao {
    @Query("SELECT * FROM entityStateComplications WHERE id = :id")
    suspend fun get(id: Int): EntityStateComplications?

    @Query("SELECT * FROM entityStateComplications")
    suspend fun getAll(): List<EntityStateComplications>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entityStateComplications: EntityStateComplications)

    @Query("DELETE FROM entityStateComplications WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM entityStateComplications")
    suspend fun deleteAll()
}