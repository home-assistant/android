package io.homeassistant.companion.android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AuthenticationDataBaseDao {

    @Insert
    fun insert(authentication: AuthenticationList)

    @Update
    fun update(authentication: AuthenticationList)

    @Query("SELECT * from Authentication_List WHERE Host = :key")
    fun get(key: String): AuthenticationList?

    @Query("DELETE FROM Authentication_List")
    fun clear()
}
