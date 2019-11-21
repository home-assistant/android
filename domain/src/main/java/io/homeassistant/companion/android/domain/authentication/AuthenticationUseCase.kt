package io.homeassistant.companion.android.domain.authentication

import java.net.URL

interface AuthenticationUseCase {

    suspend fun saveUrl(url: URL)

    suspend fun registerAuthorizationCode(authorizationCode: String)

    suspend fun retrieveExternalAuthentication(): String

    suspend fun revokeSession()

    suspend fun getSessionState(): SessionState

    suspend fun getUrl(): URL?

    suspend fun buildAuthenticationUrl(callbackUrl: String): URL
}
