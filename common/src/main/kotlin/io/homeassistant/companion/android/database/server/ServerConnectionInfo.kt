package io.homeassistant.companion.android.database.server

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.TypeConverter
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.net.URL
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

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
) {
    @Ignore
    lateinit var wifiHelper: WifiHelper

    @Ignore
    lateinit var networkHelper: NetworkHelper

    fun isRegistered(): Boolean = getApiUrls().isNotEmpty()

    fun getApiUrls(): List<URL> {
        // If we don't have a webhook id we don't have any urls.
        if (webhookId.isNullOrBlank()) {
            return emptyList()
        }

        val retVal = mutableListOf<URL?>()

        // If we are local then add the local URL in the first position, otherwise no reason to try
        if (isInternal() || prioritizeInternal) {
            internalUrl?.let {
                retVal.add(
                    it.toHttpUrlOrNull()?.newBuilder()
                        ?.addPathSegments("api/webhook/$webhookId")
                        ?.build()
                        ?.toUrl(),
                )
            }
        }

        cloudhookUrl?.let {
            retVal.add(it.toHttpUrlOrNull()?.toUrl())
        }

        externalUrl.let {
            retVal.add(
                it.toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegments("api/webhook/$webhookId")
                    ?.build()
                    ?.toUrl(),
            )
        }

        return retVal.filterNotNull()
    }

    fun getUrl(isInternal: Boolean? = null, force: Boolean = false): URL? {
        val internal = internalUrl?.toHttpUrlOrNull()?.toUrl()
        val external = externalUrl.toHttpUrlOrNull()?.toUrl()
        val cloud = cloudUrl?.toHttpUrlOrNull()?.toUrl()

        return if (isInternal ?: isInternal() && (internal != null || force)) {
            Timber.d("Using internal URL")
            internal
        } else if (!force && useCloud && cloud != null) {
            Timber.d("Using cloud / remote UI URL")
            cloud
        } else {
            Timber.d("Using external URL")
            external
        }
    }

    fun canUseCloud(): Boolean = !this.cloudUrl.isNullOrBlank()

    /**
     * Indicate if the device's current connection should be treated as internal for
     * this server.
     * @param requiresUrl Whether a valid internal url is required for internal or not.
     *   Usually you want this `true` for url related actions and `false` for others.
     */
    fun isInternal(requiresUrl: Boolean = true): Boolean {
        if (requiresUrl && internalUrl.isNullOrBlank()) return false

        if (internalEthernet == true) {
            val usesEthernet = networkHelper.isUsingEthernet()
            Timber.d("usesEthernet is: $usesEthernet")
            if (usesEthernet) return true
        }

        if (internalVpn == true) {
            val usesVpn = networkHelper.isUsingVpn()
            Timber.d("usesVpn is: $usesVpn")
            if (usesVpn) return true
        }

        return if (internalSsids.isNotEmpty()) {
            val usesInternalSsid = wifiHelper.isUsingSpecificWifi(internalSsids)
            val usesWifi = wifiHelper.isUsingWifi()
            Timber.d("usesInternalSsid is: $usesInternalSsid, usesWifi is: $usesWifi")
            usesInternalSsid && usesWifi
        } else {
            false
        }
    }
}

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
