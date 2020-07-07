package io.homeassistant.companion.android.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Authentication_List")
data class AuthenticationList(
    @PrimaryKey
    var host: String,

    @ColumnInfo(name = "Username")
    val username: String,

    @ColumnInfo(name = "Password")
    var password: String
)
