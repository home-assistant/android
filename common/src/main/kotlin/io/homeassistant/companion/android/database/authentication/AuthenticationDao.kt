package io.homeassistant.companion.android.database.authentication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AuthenticationDao {

    @Insert
    fun insert(authentication: Authentication)

    @Update
    fun update(authentication: Authentication)

    @Query("SELECT * from authentication_list WHERE Host = :key")
    fun get(key: String): Authentication?
}
