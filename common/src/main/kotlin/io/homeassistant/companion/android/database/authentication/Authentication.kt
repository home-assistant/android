package io.homeassistant.companion.android.database.authentication

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "authentication_list")
data class Authentication(
    @PrimaryKey
    val host: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password")
    val password: String,
)
