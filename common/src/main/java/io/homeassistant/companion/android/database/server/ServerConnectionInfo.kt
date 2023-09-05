package io.homeassistant.companion.android.database.server

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.TypeConverter
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

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
    @ColumnInfo(name = "prioritize_internal")
    val prioritizeInternal: Boolean = false
) {
    @Ignore
    lateinit var wifiHelper: WifiHelper

    fun isRegistered(): Boolean = getApiUrls().isNotEmpty()

    fun getApiUrls(): Array<URL> {
        // If we don't have a webhook id we don't have any urls.
        if (webhookId.isNullOrBlank()) {
            return arrayOf()
        }

        val retVal = mutableListOf<URL?>()

        // If we are local then add the local URL in the first position, otherwise no reason to try
        if (isInternal() || prioritizeInternal) {
            internalUrl?.let {
                retVal.add(
                    it.toHttpUrlOrNull()?.newBuilder()
                        ?.addPathSegments("api/webhook/$webhookId")
                        ?.build()
                        ?.toUrl()
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
                    ?.toUrl()
            )
        }

        return retVal.filterNotNull().toTypedArray()
    }

    fun getUrl(isInternal: Boolean? = null, force: Boolean = false): URL? {
        val internal = internalUrl?.toHttpUrlOrNull()?.toUrl()
        val external = externalUrl.toHttpUrlOrNull()?.toUrl()
        val cloud = cloudUrl?.toHttpUrlOrNull()?.toUrl()

        return if (isInternal ?: isInternal() && (internal != null || force)) {
            Log.d(this::class.simpleName, "Using internal URL")
            internal
        } else if (!force && useCloud && cloud != null) {
            Log.d(this::class.simpleName, "Using cloud / remote UI URL")
            cloud
        } else {
            Log.d(this::class.simpleName, "Using external URL")
            external
        }
    }

    fun canUseCloud(): Boolean = !this.cloudUrl.isNullOrBlank()

    fun isHomeWifiSsid(): Boolean = wifiHelper.isUsingSpecificWifi(internalSsids)

    fun isInternal(): Boolean {
        val usesInternalSsid = wifiHelper.isUsingSpecificWifi(internalSsids)
        val usesWifi = wifiHelper.isUsingWifi()
        val localUrl = internalUrl
        Log.d(this::class.simpleName, "localUrl is: ${!localUrl.isNullOrBlank()}, usesInternalSsid is: $usesInternalSsid, usesWifi is: $usesWifi")
        return !localUrl.isNullOrBlank() && usesInternalSsid && usesWifi
    }
}

class InternalSsidTypeConverter {
    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        return if (value == "[]" || value.isBlank()) {
            emptyList()
        } else {
            try {
                jacksonObjectMapper().readValue(value)
            } catch (e: JsonProcessingException) {
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
                jacksonObjectMapper().writeValueAsString(value)
            } catch (e: JsonProcessingException) {
                ""
            }
        }
    }
}
