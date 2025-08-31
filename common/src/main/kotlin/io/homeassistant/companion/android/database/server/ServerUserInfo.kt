package io.homeassistant.companion.android.database.server

import androidx.room.ColumnInfo

data class ServerUserInfo(
    @ColumnInfo(name = "user_id")
    val id: String? = null,
    @ColumnInfo(name = "user_name")
    val name: String? = null,
    @ColumnInfo(name = "user_is_owner")
    val isOwner: Boolean? = null,
    @ColumnInfo(name = "user_is_admin")
    val isAdmin: Boolean? = null,
)
