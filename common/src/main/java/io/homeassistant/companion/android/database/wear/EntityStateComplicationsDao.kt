package io.homeassistant.companion.android.database.wear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EntityStateComplicationsDao {
    @Query("SELECT * FROM entityStateComplications WHERE id = :id")
    suspend fun get(id: Int): EntityStateComplications?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entityStateComplications: EntityStateComplications)
}
