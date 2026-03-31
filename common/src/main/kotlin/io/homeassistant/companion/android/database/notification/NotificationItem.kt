package io.homeassistant.companion.android.database.notification

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notification_history")
data class NotificationItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    @ColumnInfo(name = "received")
    val received: Long,
    @ColumnInfo(name = "message")
    val message: String,
    @ColumnInfo(name = "data")
    val data: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "server_id")
    val serverId: Int?,
) : Serializable
