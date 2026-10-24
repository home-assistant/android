package io.homeassistant.companion.android.database.wear

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermostatTileDao {

    @Query("SELECT * FROM thermostat_tiles WHERE id = :id")
    suspend fun get(id: Int): ThermostatTile?

    @Query("SELECT * FROM thermostat_tiles ORDER BY id ASC")
    fun getAllFlow(): Flow<List<ThermostatTile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tile: ThermostatTile)

    @Query("DELETE FROM thermostat_tiles where id = :id")
    suspend fun delete(id: Int)
}
