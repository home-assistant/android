package io.homeassistant.companion.android.common.data.authentication.impl

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.url.UrlRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Named

class AuthenticationRepositoryImpl @Inject constructor(
    private val authenticationService: AuthenticationService,
    @Named("session") private val localStorage: LocalStorage,
    private val urlRepository: UrlRepository
) : AuthenticationRepository {

    companion object {
        private const val TAG = "AuthRepo"
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_EXPIRED_DATE = "expires_date"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_TOKEN_TYPE = "token_type"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val PREF_BIOMETRIC_HOME_BYPASS_ENABLED = "biometric_home_bypass_enabled"
    }

    override suspend fun registerAuthorizationCode(authorizationCode: String) {
        val url = urlRepository.getUrl()?.toHttpUrlOrNull()
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
            saveSession(
                Session(
                    it.accessToken,
                    System.currentTimeMillis() / 1000 + it.expiresIn,
                    it.refreshToken!!,
                    it.tokenType
                )
            )
        }
    }

    override suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String {
        return convertSession(ensureValidSession(forceRefresh))
    }

    override suspend fun retrieveAccessToken(): String {
        return ensureValidSession(false).accessToken
    }

    override suspend fun revokeSession() {
        val session = retrieveSession()
        val url = urlRepository.getUrl()?.toHttpUrlOrNull()
        if (session == null || url == null) {
            Log.e(TAG, "Unable to revoke session.")
            return
        }
        authenticationService.revokeToken(
            url.newBuilder().addPathSegments("auth/token").build(),
            session.refreshToken,
            AuthenticationService.REVOKE_ACTION
        )
        removeSessionData()
    }

    override suspend fun removeSessionData() {
        saveSession(null)
        urlRepository.saveUrl("", true)
        urlRepository.saveUrl("", false)
        urlRepository.updateCloudUrls(null, null)
        urlRepository.saveHomeWifiSsids(emptySet())
    }

    override suspend fun getSessionState(): SessionState {
        return if (retrieveSession() != null && urlRepository.getUrl() != null) {
            SessionState.CONNECTED
        } else {
            SessionState.ANONYMOUS
        }
    }

    override suspend fun buildAuthenticationUrl(baseUrl: String, callbackUrl: String): String {
        return baseUrl.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("auth/authorize")
            .addEncodedQueryParameter("response_type", "code")
            .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
            .addEncodedQueryParameter("redirect_uri", callbackUrl)
            .build()
            .toString()
    }

    override suspend fun buildBearerToken(): String {
        return "Bearer " + ensureValidSession().accessToken
    }

    private fun convertSession(session: Session): String {
        return ObjectMapper().writeValueAsString(
            mapOf(
                "access_token" to session.accessToken,
                "expires_in" to session.expiresIn()
            )
        )
    }

    private suspend fun retrieveSession(): Session? {
        val accessToken = localStorage.getString(PREF_ACCESS_TOKEN)
        val expiredDate = localStorage.getLong(PREF_EXPIRED_DATE)
        val refreshToken = localStorage.getString(PREF_REFRESH_TOKEN)
        val tokenType = localStorage.getString(PREF_TOKEN_TYPE)

        return if (accessToken != null && expiredDate != null && refreshToken != null && tokenType != null) {
            Session(accessToken, expiredDate, refreshToken, tokenType)
        } else {
            null
        }
    }

    private suspend fun ensureValidSession(forceRefresh: Boolean = false): Session {
        val session = retrieveSession()
        val url = urlRepository.getUrl()?.toHttpUrlOrNull()
        if (session == null || url == null) {
            Log.e(TAG, "Unable to revoke session.")
            throw AuthorizationException()
        }

        if (session.isExpired() || forceRefresh) {
            return authenticationService.refreshToken(
                url.newBuilder().addPathSegments("auth/token").build(),
                AuthenticationService.GRANT_TYPE_REFRESH,
                session.refreshToken,
                AuthenticationService.CLIENT_ID
            ).let {
                if (it.isSuccessful) {
                    val refreshedToken = it.body() ?: throw AuthorizationException()
                    val refreshSession = Session(
                        refreshedToken.accessToken,
                        System.currentTimeMillis() / 1000 + refreshedToken.expiresIn,
                        session.refreshToken,
                        refreshedToken.tokenType
                    )
                    saveSession(refreshSession)
                    return@let refreshSession
                } else if (it.code() == 400 &&
                    it.errorBody()?.string()?.contains("invalid_grant") == true
                ) {
                    revokeSession()
                }
                throw AuthorizationException()
            }
        }

        return session
    }

    private suspend fun saveSession(session: Session?) {
        localStorage.putString(PREF_ACCESS_TOKEN, session?.accessToken)
        localStorage.putLong(PREF_EXPIRED_DATE, session?.expiresTimestamp)
        localStorage.putString(PREF_REFRESH_TOKEN, session?.refreshToken)
        localStorage.putString(PREF_TOKEN_TYPE, session?.tokenType)
    }

    override suspend fun setLockEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_BIOMETRIC_ENABLED, enabled)
    }

    override suspend fun setLockHomeBypassEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_BIOMETRIC_HOME_BYPASS_ENABLED, enabled)
    }

    override suspend fun isLockEnabledRaw(): Boolean {
        return localStorage.getBoolean(PREF_BIOMETRIC_ENABLED)
    }

    override suspend fun isLockHomeBypassEnabled(): Boolean {
        return localStorage.getBoolean(PREF_BIOMETRIC_HOME_BYPASS_ENABLED)
    }

    override suspend fun isLockEnabled(): Boolean {
        val raw = isLockEnabledRaw()
        val bypass = isLockHomeBypassEnabled()
        if (raw && bypass) {
            return !(urlRepository.isHomeWifiSsid())
        } else {
            return raw
        }
    }
}
