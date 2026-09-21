package io.homeassistant.companion.android.database.server

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: Int): Server?

    @Query("SELECT * FROM servers WHERE webhook_id = :webhookId")
    suspend fun get(webhookId: String): Server?

    @Query("SELECT * FROM servers WHERE id = :serverId")
    fun getFlow(serverId: Int): Flow<Server?>

    @Query("SELECT * FROM servers ORDER BY `list_order` ASC")
    suspend fun getAll(): List<Server>

    @Query("SELECT id FROM servers ORDER BY id DESC LIMIT 1")
    suspend fun getLastServerId(): Int?

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
