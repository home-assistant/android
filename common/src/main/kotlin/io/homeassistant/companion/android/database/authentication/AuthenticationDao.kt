package io.homeassistant.companion.android.database.authentication

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface AuthenticationDao {

    @Insert
    suspend fun insert(authentication: Authentication)

    @Update
    suspend fun update(authentication: Authentication)

    @Query("SELECT * from authentication_list WHERE Host = :key")
    suspend fun get(key: String): Authentication?
}
