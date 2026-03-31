package io.homeassistant.companion.android.common.data.authentication

import dagger.assisted.AssistedFactory
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationRepositoryImpl

interface AuthenticationRepository {

    suspend fun registerRefreshToken(refreshToken: String)

    suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String

    suspend fun retrieveAccessToken(): String

    suspend fun revokeSession()

    suspend fun deletePreferences()

    suspend fun getSessionState(): SessionState

    suspend fun buildBearerToken(): String

    suspend fun setLockEnabled(enabled: Boolean)
    suspend fun setLockHomeBypassEnabled(enabled: Boolean)
    suspend fun isLockEnabledRaw(): Boolean
    suspend fun isLockHomeBypassEnabled(): Boolean
    suspend fun isLockEnabled(): Boolean
}

@AssistedFactory
internal interface AuthenticationRepositoryFactory {
    fun create(serverId: Int): AuthenticationRepositoryImpl
}
