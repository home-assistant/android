package io.homeassistant.companion.android.database.server

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.homeassistant.companion.android.common.data.HomeAssistantVersion

@Entity(tableName = "servers")
@TypeConverters(InternalSsidTypeConverter::class)
data class Server(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "_version")
    val _version: String? = null,
    @Ignore
    val type: ServerType = ServerType.DEFAULT,
    @ColumnInfo(name = "order")
    val order: Int = -1,
    @Embedded val connection: ServerConnectionInfo,
    @Embedded val session: ServerSessionInfo
) {
    constructor(id: Int, name: String, _version: String?, order: Int, connection: ServerConnectionInfo, session: ServerSessionInfo) :
        this(id, name, _version, ServerType.DEFAULT, order, connection, session)

    val version: HomeAssistantVersion?
        get() = _version?.let { HomeAssistantVersion.fromString(_version) }
}

enum class ServerType {
    TEMPORARY, DEFAULT
}
