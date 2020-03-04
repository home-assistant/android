package io.homeassistant.companion.android.domain.authentication

import java.net.URL

interface AuthenticationRepository {

    suspend fun registerAuthorizationCode(authorizationCode: String)

    suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String

    suspend fun revokeSession()

    suspend fun getSessionState(): SessionState

    suspend fun buildAuthenticationUrl(callbackUrl: String): URL

    suspend fun buildBearerToken(): String

    suspend fun setLockEnabled(enabled: Boolean)
    suspend fun isLockEnabled(): Boolean
}
