package io.homeassistant.companion.android.domain.authentication

import java.net.URL

interface AuthenticationUseCase {

    suspend fun registerAuthorizationCode(authorizationCode: String)

    suspend fun retrieveExternalAuthentication(forceRefresh: Boolean = false): String

    suspend fun revokeSession()

    suspend fun getSessionState(): SessionState

    suspend fun buildAuthenticationUrl(callbackUrl: String): URL
}
