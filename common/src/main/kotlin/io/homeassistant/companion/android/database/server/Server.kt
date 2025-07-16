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
    @ColumnInfo(name = "_name")
    val _name: String,
    @ColumnInfo(name = "name_override")
    val nameOverride: String? = null,
    @ColumnInfo(name = "_version")
    val _version: String? = null,
    @ColumnInfo(name = "device_registry_id")
    val deviceRegistryId: String? = null,
    @Ignore
    val type: ServerType = ServerType.DEFAULT,
    @ColumnInfo(name = "list_order")
    val listOrder: Int = -1,
    @ColumnInfo(name = "device_name")
    val deviceName: String? = null,
    @Embedded val connection: ServerConnectionInfo,
    @Embedded val session: ServerSessionInfo,
    @Embedded val user: ServerUserInfo,
) {
    constructor(
        id: Int,
        _name: String,
        nameOverride: String?,
        _version: String?,
        deviceRegistryId: String?,
        listOrder: Int,
        deviceName: String?,
        connection: ServerConnectionInfo,
        session: ServerSessionInfo,
        user: ServerUserInfo,
    ) :
        this(
            id,
            _name,
            nameOverride,
            _version,
            deviceRegistryId,
            ServerType.DEFAULT,
            listOrder,
            deviceName,
            connection,
            session,
            user,
        )

    val friendlyName: String
        get() = nameOverride ?: _name.ifBlank { connection.externalUrl }

    val version: HomeAssistantVersion?
        get() = _version?.let { HomeAssistantVersion.fromString(_version) }
}

enum class ServerType {
    TEMPORARY,
    DEFAULT,
}
