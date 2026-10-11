package io.homeassistant.companion.android.database.server

import android.os.Parcelable
import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import kotlinx.parcelize.Parcelize

@Entity(tableName = "servers")
@ColumnTypeConverters(InternalSsidColumnTypeConverter::class)
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
    @ColumnInfo(name = "list_order")
    val listOrder: Int = -1,
    @ColumnInfo(name = "device_name")
    val deviceName: String? = null,
    @Embedded val connection: ServerConnectionInfo,
    @Embedded val session: ServerSessionInfo,
    @Embedded val user: ServerUserInfo,
) {

    companion object {
        fun fromTemporaryServer(temporaryServer: TemporaryServer): Server {
            return Server(
                _name = "",
                connection = ServerConnectionInfo(
                    externalUrl = temporaryServer.externalUrl,
                    allowInsecureConnection = temporaryServer.allowInsecureConnection,
                ),
                session = temporaryServer.session,
                user = ServerUserInfo(),
            )
        }
    }

    val friendlyName: String
        get() = nameOverride ?: _name.ifBlank { connection.externalUrl }

    val version: HomeAssistantVersion?
        get() = _version?.let { HomeAssistantVersion.fromString(_version) }
}

@Parcelize
data class TemporaryServer(
    val externalUrl: String,
    val session: ServerSessionInfo,
    val allowInsecureConnection: Boolean?,
) : Parcelable
