package io.homeassistant.companion.android.database.server

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.TypeConverter
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.util.hasSameOrigin
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Holds connection configuration for a Home Assistant server.
 *
 * This data class is embedded in [Server] and stored in Room database. It contains all URL
 * endpoints and network detection settings used to connect to the server.
 *
 * @property externalUrl The primary URL used to connect to the server from outside the home network.
 * @property internalUrl Optional URL used when the device is on the home network.
 * @property cloudUrl Optional Home Assistant Cloud remote UI URL, automatically updated from the server.
 * @property webhookId Unique identifier assigned by Home Assistant during device registration.
 * @property secret Shared secret for webhook authentication, if configured on the server.
 * @property cloudhookUrl Direct webhook URL provided by Home Assistant Cloud.
 * @property useCloud Whether to prefer the cloud URL over the external URL when not on the
 *   home network.
 * @property internalSsids List of Wi-Fi network names (SSIDs) that indicate the device is on
 *   the home network.
 * @property internalEthernet When `true`, Ethernet connections are treated as being on the home
 *   network. `null` means the user hasn't configured this setting.
 * @property internalVpn When `true`, VPN connections are treated as being on the home network.
 *   `null` means the user hasn't configured this setting.
 * @property prioritizeInternal When `true`, always try the internal URL first regardless of
 *   detected network state.
 * @property allowInsecureConnection Controls whether non-HTTPS connections are permitted
 *   when not on the home network. `null` means the user hasn't set their preference yet (legacy
 *   installs), `true` allows insecure connections, when `false` insecure connections should be blocked.
 */
data class ServerConnectionInfo(
    @ColumnInfo(name = "external_url")
    val externalUrl: String,
    @ColumnInfo(name = "internal_url")
    val internalUrl: String? = null,
    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null,
    @ColumnInfo(name = "webhook_id")
    val webhookId: String? = null,
    @ColumnInfo(name = "secret")
    val secret: String? = null,
    @ColumnInfo(name = "cloudhook_url")
    val cloudhookUrl: String? = null,
    @ColumnInfo(name = "use_cloud")
    val useCloud: Boolean = false,
    @ColumnInfo(name = "internal_ssids")
    val internalSsids: List<String> = emptyList(),
    @ColumnInfo(name = "internal_ethernet")
    val internalEthernet: Boolean? = null,
    @ColumnInfo(name = "internal_vpn")
    val internalVpn: Boolean? = null,
    @ColumnInfo(name = "prioritize_internal")
    val prioritizeInternal: Boolean = false,
    @ColumnInfo(name = "allow_insecure_connection")
    val allowInsecureConnection: Boolean? = null,
) {
    // Keep the parsing of the URLs from String to HttpUrl to avoid doing it multiple times
    @get:Ignore
    internal val externalHttpUrl: HttpUrl? by lazy { externalUrl.toHttpUrlOrNull() }

    @get:Ignore
    internal val internalHttpUrl: HttpUrl? by lazy { internalUrl?.toHttpUrlOrNull() }

    @get:Ignore
    internal val cloudHttpUrl: HttpUrl? by lazy { cloudUrl?.toHttpUrlOrNull() }

    @get:Ignore
    internal val cloudhookHttpUrl: HttpUrl? by lazy { cloudhookUrl?.toHttpUrlOrNull() }

    @get:Ignore
    internal val httpUrls: List<HttpUrl> by lazy {
        listOfNotNull(
            externalHttpUrl,
            internalHttpUrl,
            cloudHttpUrl,
            cloudhookHttpUrl,
        )
    }

    /**
     * Indicates whether at least one URL is configured and parsable.
     */
    @get:Ignore
    val hasAtLeastOneUrl: Boolean by lazy {
        httpUrls.isNotEmpty()
    }

    /**
     * Indicates whether the user has configured any home network detection method.
     *
     * Returns `true` if at least one of the following is set:
     * - One or more internal SSIDs
     * - Ethernet as internal network (`internalEthernet = true`)
     * - VPN as internal network (`internalVpn = true`)
     */
    @Ignore
    val hasHomeNetworkSetup: Boolean = internalSsids.isNotEmpty() ||
        internalVpn == true ||
        internalEthernet == true

    /**
     * Checks if the server is registered and has at least one valid API URL.
     *
     * A server is considered registered if it has a webhook ID and at least one
     * of its URLs (external, internal, or cloudhook) can be parsed successfully.
     *
     * @return `true` if the server is registered with valid URLs
     */
    @get:Ignore
    val isRegistered: Boolean by lazy {
        !webhookId.isNullOrBlank() && httpUrls.isNotEmpty()
    }

    /**
     * Checks if any of the configured URLs use plain HTTP (non-HTTPS).
     *
     * @return `true` if external, internal, or cloud URL uses HTTP
     */
    @Ignore
    val hasPlainTextUrl: Boolean = listOfNotNull(
        externalUrl,
        internalUrl,
        cloudUrl,
    ).any { it.startsWith("http://") }

    /**
     * Checks if cloud/remote UI URL is configured.
     *
     * @return `true` if a cloud URL is available
     */
    @Ignore
    val canUseCloud: Boolean = !cloudUrl.isNullOrBlank()

    /**
     * Checks if the given URL belongs to this server by matching against configured URLs.
     *
     * Compares scheme, host, and port to determine if the URL belongs to one of the server's
     * configured endpoints.
     *
     * @param url the URL to check (must be a valid URL string)
     * @return `true` if the URL belongs to this server
     */
    fun isKnownUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return httpUrls.any { httpUrl.hasSameOrigin(it) }
    }
}

/**
 * Room [TypeConverter] for serializing SSID lists to/from JSON strings.
 *
 * Converts `List<String>` to a JSON array string for database storage and vice versa.
 * Handles edge cases like empty lists, blank strings, and invalid JSON gracefully.
 */
class InternalSsidTypeConverter {
    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        return if (value == "[]" || value.isBlank()) {
            emptyList()
        } else {
            try {
                kotlinJsonMapper.decodeFromString(value)
            } catch (_: SerializationException) {
                emptyList()
            }
        }
    }

    @TypeConverter
    fun fromListToString(value: List<String>): String {
        return if (value.isEmpty()) {
            "[]"
        } else {
            try {
                kotlinJsonMapper.encodeToString(value)
            } catch (_: SerializationException) {
                ""
            }
        }
    }
}
