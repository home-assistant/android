package io.homeassistant.companion.android.domain.url

import java.net.URL
import javax.inject.Inject

class UrlUseCaseImpl @Inject constructor(
    private val urlRepository: UrlRepository
) : UrlUseCase {
    override suspend fun getApiUrls(): Array<URL> {
        return urlRepository.getApiUrls()
    }

    override suspend fun saveRegistrationUrls(
        cloudHookUrl: String,
        remoteUiUrl: String,
        webhookId: String
    ) {
        urlRepository.saveRegistrationUrls(cloudHookUrl, remoteUiUrl, webhookId)
    }

    override suspend fun getUrl(isInternal: Boolean?): URL? {
        return urlRepository.getUrl(isInternal)
    }

    override suspend fun saveUrl(url: String, isInternal: Boolean?) {
        urlRepository.saveUrl(url, isInternal)
    }

    override suspend fun getHomeWifiSsid(): String? {
        return urlRepository.getHomeWifiSsid()
    }

    override suspend fun saveHomeWifiSsid(ssid: String?) {
        urlRepository.saveHomeWifiSsid(ssid)
    }
}
