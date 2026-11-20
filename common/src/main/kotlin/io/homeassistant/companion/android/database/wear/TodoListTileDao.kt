package io.homeassistant.companion.android.database.wear

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoListTileDao {

    @Query("SELECT * FROM todo_list_tiles WHERE id = :id")
    suspend fun get(id: Int): TodoListTile?

    @Query("SELECT * FROM todo_list_tiles ORDER BY id ASC")
    fun getAllFlow(): Flow<List<TodoListTile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(tile: TodoListTile)

    @Query("DELETE FROM todo_list_tiles where id = :id")
    suspend fun delete(id: Int)
}
