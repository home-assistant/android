package io.homeassistant.companion.android.common.data.authentication.impl

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService.Companion.SEGMENT_AUTH_TOKEN
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.datastore.ServerSession
import io.homeassistant.companion.android.datastore.SessionDatastore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

class AuthenticationRepositoryImpl internal constructor(
    private val authenticationService: AuthenticationService,
    private val serverManager: ServerManager,
    private val serverId: Int,
    private val localStorage: LocalStorage,
    private val installId: String,
    private val sessionDatastore: SessionDatastore,
    private val clock: Clock,
) : AuthenticationRepository {

    companion object {
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val PREF_BIOMETRIC_HOME_BYPASS_ENABLED = "biometric_home_bypass_enabled"
    }

    private suspend fun server(): Server {
        return checkNotNull(serverManager.getServer(serverId)) { "No server found for id $serverId" }
    }

    private suspend fun connectionStateProvider() = serverManager.connectionStateProvider(serverId)

    /**
     * The stored session, or `null` when this install has none for the server. A session belonging
     * to another install counts as absent: its webhook is not ours to use.
     */
    private suspend fun session(): ServerSession? =
        sessionDatastore.getSession(serverId)?.takeIf { server().installId == installId }

    private fun ServerSession.isExpired() = tokenExpiration <= clock.now()

    override suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String {
        ensureValidSession(forceRefresh)
        val session = session() ?: throw AuthorizationException()
        return kotlinJsonMapper.encodeToString(
            MapAnySerializer,
            mapOf(
                "access_token" to session.accessToken,
                "expires_in" to (session.tokenExpiration - clock.now()).inWholeSeconds,
            ),
        )
    }

    override suspend fun retrieveAccessToken(): String {
        ensureValidSession(false)
        return (session() ?: throw AuthorizationException()).accessToken
    }

    override suspend fun revokeSession() {
        val server = server()
        val url = connectionStateProvider().urlFlow().firstUrlOrNull {
            "No URL available to revoke session"
        }?.toHttpUrlOrNull()
        val session = session()
        if (session == null || url == null) {
            Timber.e("Unable to revoke session.")
            return
        }
        if (server.version?.isAtLeast(2022, 9, 0) == true) {
            authenticationService.revokeToken(
                url.newBuilder().addPathSegments("auth/revoke").build(),
                session.refreshToken,
            )
        } else {
            authenticationService.revokeTokenLegacy(
                url.newBuilder().addPathSegments(SEGMENT_AUTH_TOKEN).build(),
                session.refreshToken,
                AuthenticationService.REVOKE_ACTION,
            )
        }
        sessionDatastore.removeSession(serverId)
    }

    override suspend fun deletePreferences() {
        localStorage.remove("${serverId}_$PREF_BIOMETRIC_ENABLED")
        localStorage.remove("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED")
    }

    override suspend fun getSessionState(): SessionState {
        val server = server()
        return if (session() != null && server.connection.hasAtLeastOneUrl) {
            SessionState.CONNECTED
        } else {
            SessionState.ANONYMOUS
        }
    }

    override suspend fun buildBearerToken(): String {
        ensureValidSession()
        return "Bearer " + (session() ?: throw AuthorizationException()).accessToken
    }

    private suspend fun ensureValidSession(forceRefresh: Boolean = false) {
        val url = connectionStateProvider().urlFlow().firstUrlOrNull()?.toHttpUrlOrNull()
        val session = session()
        if (session == null || url == null) {
            Timber.e("Unable to ensure valid session.")
            throw AuthorizationException()
        }

        if (session.isExpired() || forceRefresh) {
            refreshSessionWithToken(url, session.refreshToken)
        }
    }

    private suspend fun refreshSessionWithToken(baseUrl: HttpUrl, refreshToken: String) {
        return authenticationService.refreshToken(
            baseUrl.newBuilder().addPathSegments(SEGMENT_AUTH_TOKEN).build(),
            AuthenticationService.GRANT_TYPE_REFRESH,
            refreshToken,
            AuthenticationService.CLIENT_ID,
        ).let {
            if (it.isSuccessful) {
                val refreshedToken = it.body() ?: throw AuthorizationException()
                sessionDatastore.updateSession(
                    serverId,
                    ServerSession(
                        accessToken = refreshedToken.accessToken,
                        refreshToken = refreshToken,
                        tokenExpiration = clock.now() + refreshedToken.expiresIn.seconds,
                        tokenType = refreshedToken.tokenType,
                    ),
                )
                return@let
            } else if (it.code() == 400 &&
                it.errorBody()?.string()?.contains("invalid_grant") == true
            ) {
                revokeSession()
            }
            throw AuthorizationException("Failed to refresh token", it.code(), it.errorBody())
        }
    }

    override suspend fun setLockEnabled(enabled: Boolean) =
        localStorage.putBoolean("${serverId}_$PREF_BIOMETRIC_ENABLED", enabled)

    override suspend fun setLockHomeBypassEnabled(enabled: Boolean) =
        localStorage.putBoolean("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED", enabled)

    override suspend fun isLockEnabledRaw(): Boolean = localStorage.getBoolean("${serverId}_$PREF_BIOMETRIC_ENABLED")

    override suspend fun isLockHomeBypassEnabled(): Boolean =
        localStorage.getBoolean("${serverId}_$PREF_BIOMETRIC_HOME_BYPASS_ENABLED")

    override suspend fun isLockEnabled(): Boolean {
        val raw = isLockEnabledRaw()
        val bypass = isLockHomeBypassEnabled()
        return if (raw && bypass) {
            !connectionStateProvider().isInternal(requiresUrl = false)
        } else {
            raw
        }
    }
}
