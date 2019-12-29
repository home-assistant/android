package io.homeassistant.companion.android.domain.url

import java.net.URL

interface UrlUseCase {

    suspend fun getApiUrls(): Array<URL>

    suspend fun saveRegistrationUrls(cloudHookUrl: String, remoteUiUrl: String, webhookId: String)

    suspend fun getUrl(isInternal: Boolean): URL?

    suspend fun saveUrl(isInternal: Boolean, url: String)

}
