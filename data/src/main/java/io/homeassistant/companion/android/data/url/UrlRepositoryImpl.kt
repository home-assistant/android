package io.homeassistant.companion.android.data.url

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.data.wifi.WifiHelper
import io.homeassistant.companion.android.domain.MalformedHttpUrlException
import io.homeassistant.companion.android.domain.url.UrlRepository
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class UrlRepositoryImpl @Inject constructor(
    @Named("url") private val localStorage: LocalStorage,
    private val wifiHelper: WifiHelper
) : UrlRepository {

    companion object {
        private const val PREF_CLOUDHOOK_URL = "cloudhook_url"
        private const val PREF_REMOTE_URL = "remote_url"
        private const val PREF_WEBHOOK_ID = "webhook_id"
        private const val PREF_LOCAL_URL = "local_url"
        private const val PREF_WIFI_SSIDS = "wifi_ssids"
    }

    override suspend fun getApiUrls(): Array<URL> {
        val retVal = ArrayList<URL>()
        val webhook = localStorage.getString(PREF_WEBHOOK_ID)

        // If we don't have a webhook id we don't have any urls.
        if (webhook.isNullOrBlank()) {
            return arrayOf()
        }

        localStorage.getString(PREF_CLOUDHOOK_URL)?.let {
            retVal.add(it.toHttpUrl().toUrl())
        }

        localStorage.getString(PREF_REMOTE_URL)?.let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
                    .toUrl()
            )
        }

        localStorage.getString(PREF_LOCAL_URL)?.let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
                    .toUrl()
            )
        }

        return retVal.toTypedArray()
    }

    override suspend fun saveRegistrationUrls(
        cloudHookUrl: String?,
        remoteUiUrl: String?,
        webhookId: String
    ) {
        localStorage.putString(PREF_CLOUDHOOK_URL, cloudHookUrl)
        localStorage.putString(PREF_WEBHOOK_ID, webhookId)
        remoteUiUrl?.let {
            localStorage.putString(PREF_REMOTE_URL, it)
        }
    }

    override suspend fun getUrl(isInternal: Boolean?): URL? {
        val internal = localStorage.getString(PREF_LOCAL_URL)?.toHttpUrlOrNull()?.toUrl()
        val external = localStorage.getString(PREF_REMOTE_URL)?.toHttpUrlOrNull()?.toUrl()

        return if (isInternal ?: isInternal()) {
            internal
        } else {
            external
        }
    }

    override suspend fun saveUrl(url: String, isInternal: Boolean?) {
        val trimUrl = if (url == "") null else try {
            val httpUrl = url.toHttpUrl()
            HttpUrl.Builder()
                .scheme(httpUrl.scheme)
                .host(httpUrl.host)
                .port(httpUrl.port)
                .toString()
        } catch (e: IllegalArgumentException) {
            throw MalformedHttpUrlException(
                e.message
            )
        }
        localStorage.putString(if (isInternal ?: isInternal()) PREF_LOCAL_URL else PREF_REMOTE_URL, trimUrl)
    }

    override suspend fun getHomeWifiSsids(): Set<String> {
        return localStorage.getStringSet(PREF_WIFI_SSIDS) ?: emptySet()
    }

    override suspend fun saveHomeWifiSsids(ssid: Set<String>) {
        localStorage.putStringSet(PREF_WIFI_SSIDS, ssid)
    }

    private suspend fun isInternal(): Boolean {
        val formattedSsid = wifiHelper.getWifiSsid().removeSurrounding("\"")
        val wifiSsids = getHomeWifiSsids()
        val usesInternalSsid = formattedSsid in wifiSsids
        val localUrl = localStorage.getString(PREF_LOCAL_URL)
        return !localUrl.isNullOrBlank() && usesInternalSsid
    }
}
