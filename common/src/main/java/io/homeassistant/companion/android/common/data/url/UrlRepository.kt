package io.homeassistant.companion.android.common.data.url

import java.net.URL

interface UrlRepository {

    companion object {
        const val BSSID_PREFIX = "BSSID:"
        const val INVALID_BSSID = "02:00:00:00:00:00"
    }

    suspend fun getWebhookId(): String?

    suspend fun getApiUrls(): Array<URL>

    suspend fun saveRegistrationUrls(cloudHookUrl: String?, remoteUiUrl: String?, webhookId: String)

    suspend fun getUrl(isInternal: Boolean? = null): URL?

    suspend fun saveUrl(url: String, isInternal: Boolean? = null)

    suspend fun getHomeWifiSsids(): Set<String>

    suspend fun saveHomeWifiSsids(ssid: Set<String>)

    suspend fun isHomeWifiSsid(): Boolean

    suspend fun isInternal(): Boolean

    suspend fun isPrioritizeInternal(): Boolean

    suspend fun setPrioritizeInternal(enabled: Boolean)
}
