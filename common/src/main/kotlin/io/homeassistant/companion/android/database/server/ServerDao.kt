package io.homeassistant.companion.android.database.server

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: Int): Server?

    @Query("SELECT * FROM servers WHERE webhook_id = :webhookId")
    suspend fun get(webhookId: String): Server?

    @Query("SELECT * FROM servers ORDER BY `list_order` ASC")
    suspend fun getAll(): List<Server>

    @Query("SELECT * FROM servers ORDER BY `list_order` ASC")
    fun getAllFlow(): Flow<List<Server>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(server: Server): Long

    @Update
    suspend fun update(server: Server)

    @Update
    suspend fun update(servers: List<Server>)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM servers WHERE webhook_id = :webhookId")
    suspend fun delete(webhookId: String)
}
