package io.homeassistant.companion.android.database.authentication

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Authentication_List")
data class Authentication(
    @PrimaryKey
    val host: String,

    @ColumnInfo(name = "Username")
    val username: String,

    @ColumnInfo(name = "Password")
    val password: String
)
