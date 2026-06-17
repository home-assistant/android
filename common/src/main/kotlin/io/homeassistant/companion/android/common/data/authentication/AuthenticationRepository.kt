package io.homeassistant.companion.android.common.data.authentication

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.di.qualifiers.NamedInstallId
import io.homeassistant.companion.android.di.qualifiers.NamedSessionStorage
import javax.inject.Inject
import javax.inject.Provider

interface AuthenticationRepository {

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

internal class AuthenticationRepositoryFactory @Inject constructor(
    private val authenticationServiceProvider: SuspendProvider<AuthenticationService>,
    // Use a Provider to avoid a dependency circle since serverManager needs the factory
    private val serverManagerProvider: Provider<ServerManager>,
    @NamedSessionStorage private val localStorage: LocalStorage,
    @NamedInstallId private val installIdProvider: SuspendProvider<String>,
) {
    suspend fun create(serverId: Int): AuthenticationRepositoryImpl {
        return AuthenticationRepositoryImpl(
            authenticationService = authenticationServiceProvider(),
            serverManager = serverManagerProvider.get(),
            serverId = serverId,
            localStorage = localStorage,
            installId = installIdProvider(),
        )
    }
}
