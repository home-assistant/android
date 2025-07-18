package io.homeassistant.companion.android.database.authentication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AuthenticationDao {

    @Insert
    suspend fun insert(authentication: Authentication)

    @Update
    suspend fun update(authentication: Authentication)

    @Query("SELECT * from authentication_list WHERE Host = :key")
    suspend fun get(key: String): Authentication?
}
