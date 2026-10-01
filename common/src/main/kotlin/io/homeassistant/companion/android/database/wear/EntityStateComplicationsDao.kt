package io.homeassistant.companion.android.database.wear

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface EntityStateComplicationsDao {
    @Query("SELECT * FROM entity_state_complications WHERE id = :id")
    suspend fun get(id: Int): EntityStateComplications?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entityStateComplications: EntityStateComplications)
}
