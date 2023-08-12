package io.homeassistant.companion.android.common.data.authentication.impl

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Named

class AuthenticationRepositoryImpl @AssistedInject constructor(
    private val authenticationService: AuthenticationService,
    private val serverManager: ServerManager,
    @Assisted private val serverId: Int,
    @Named("session") private val localStorage: LocalStorage,
    @Named("installId") private val installId: String
) : AuthenticationRepository {

    companion object {
        private const val TAG = "AuthRepo"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val PREF_BIOMETRIC_HOME_BYPASS_ENABLED = "biometric_home_bypass_enabled"
    }

    private val server get() = serverManager.getServer(serverId)!!

    override suspend fun registerAuthorizationCode(authorizationCode: String) {
        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (url == null) {
            Log.e(TAG, "Unable to register auth code.")
            return
        }
        authenticationService.getToken(
            url.newBuilder().addPathSegments("auth/token").build(),
            AuthenticationService.GRANT_TYPE_CODE,
            authorizationCode,
            AuthenticationService.CLIENT_ID
        ).let {
            serverManager.updateServer(
                server.copy(
                    session = ServerSessionInfo(
                        it.accessToken,
                        it.refreshToken!!,
                        System.currentTimeMillis() / 1000 + it.expiresIn,
                        it.tokenType,
                        installId
                    )
                )
            )
        }
    }

    override suspend fun registerRefreshToken(refreshToken: String) {
        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (url == null) {
            Log.e(TAG, "Unable to register session with refresh token.")
            return
        }
        refreshSessionWithToken(refreshToken)
    }

    override suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String {
        ensureValidSession(forceRefresh)
        return jacksonObjectMapper().writeValueAsString(
            mapOf(
                "access_token" to server.session.accessToken,
                "expires_in" to server.session.expiresIn()
            )
        )
    }

    override suspend fun retrieveAccessToken(): String {
        ensureValidSession(false)
        return server.session.accessToken!!
    }

    override suspend fun revokeSession() {
        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (!server.session.isComplete() || url == null) {
            Log.e(TAG, "Unable to revoke session.")
            return
        }
        if (server.version?.isAtLeast(2022, 9, 0) == true) {
            authenticationService.revokeToken(
                url.newBuilder().addPathSegments("auth/revoke").build(),
                server.session.refreshToken!!
            )
        } else {
            authenticationService.revokeTokenLegacy(
                url.newBuilder().addPathSegments("auth/token").build(),
                server.session.refreshToken!!,
                AuthenticationService.REVOKE_ACTION
            )
        }
        serverManager.updateServer(
            server.copy(
                session = server.session.copy(
                    refreshToken = null
                )
            )
        )
    }

    override suspend fun deletePreferences() {
        localStorage.remove("${serverId}_$PREF_BIOMETRIC_ENABLED")
        localStorage.remove("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED")
    }

    override fun getSessionState(): SessionState {
        return if (server.session.isComplete() && server.session.installId == installId && server.connection.getUrl() != null) {
            SessionState.CONNECTED
        } else {
            SessionState.ANONYMOUS
        }
    }

    override suspend fun buildAuthenticationUrl(baseUrl: String): String {
        return baseUrl.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("auth/authorize")
            .addEncodedQueryParameter("response_type", "code")
            .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
            .build()
            .toString()
    }

    override suspend fun buildBearerToken(): String {
        ensureValidSession()
        return "Bearer " + server.session.accessToken
    }

    private suspend fun ensureValidSession(forceRefresh: Boolean = false) {
        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (!server.session.isComplete() || server.session.installId != installId || url == null) {
            Log.e(TAG, "Unable to ensure valid session.")
            throw AuthorizationException()
        }

        if (server.session.isExpired() || forceRefresh) {
            refreshSessionWithToken(server.session.refreshToken!!)
        }
    }

    private suspend fun refreshSessionWithToken(refreshToken: String) {
        return authenticationService.refreshToken(
            server.connection.getUrl()?.toHttpUrlOrNull()!!.newBuilder().addPathSegments("auth/token").build(),
            AuthenticationService.GRANT_TYPE_REFRESH,
            refreshToken,
            AuthenticationService.CLIENT_ID
        ).let {
            if (it.isSuccessful) {
                val refreshedToken = it.body() ?: throw AuthorizationException()
                serverManager.updateServer(
                    server.copy(
                        session = ServerSessionInfo(
                            refreshedToken.accessToken,
                            refreshToken,
                            System.currentTimeMillis() / 1000 + refreshedToken.expiresIn,
                            refreshedToken.tokenType,
                            installId
                        )
                    )
                )
                return@let
            } else if (it.code() == 400 &&
                it.errorBody()?.string()?.contains("invalid_grant") == true
            ) {
                revokeSession()
            }
            throw AuthorizationException()
        }
    }

    override suspend fun setLockEnabled(enabled: Boolean) =
        localStorage.putBoolean("${serverId}_$PREF_BIOMETRIC_ENABLED", enabled)

    override suspend fun setLockHomeBypassEnabled(enabled: Boolean) =
        localStorage.putBoolean("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED", enabled)

    override suspend fun isLockEnabledRaw(): Boolean =
        localStorage.getBoolean("${serverId}_$PREF_BIOMETRIC_ENABLED")

    override suspend fun isLockHomeBypassEnabled(): Boolean =
        localStorage.getBoolean("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED")

    override suspend fun isLockEnabled(): Boolean {
        val raw = isLockEnabledRaw()
        val bypass = isLockHomeBypassEnabled()
        return if (raw && bypass) {
            !server.connection.isHomeWifiSsid()
        } else {
            raw
        }
    }
}
