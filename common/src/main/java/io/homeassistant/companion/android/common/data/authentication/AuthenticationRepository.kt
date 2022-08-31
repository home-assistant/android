package io.homeassistant.companion.android.common.data.authentication

interface AuthenticationRepository {

    suspend fun registerAuthorizationCode(authorizationCode: String)

    suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String

    suspend fun retrieveAccessToken(): String

    suspend fun revokeSession()
    suspend fun removeSessionData()

    suspend fun getSessionState(): SessionState

    suspend fun buildAuthenticationUrl(baseUrl: String, callbackUrl: String): String

    suspend fun buildBearerToken(): String

    suspend fun setLockEnabled(enabled: Boolean)
    suspend fun isLockEnabled(): Boolean
}
