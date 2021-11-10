package io.homeassistant.companion.android.common.data.authentication.impl

import com.fasterxml.jackson.databind.ObjectMapper
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowAuthentication
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowRequest
import io.homeassistant.companion.android.common.data.url.UrlRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL
import javax.inject.Inject
import javax.inject.Named

class AuthenticationRepositoryImpl @Inject constructor(
    private val authenticationService: AuthenticationService,
    @Named("session") private val localStorage: LocalStorage,
    private val urlRepository: UrlRepository
) : AuthenticationRepository {

    companion object {
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_EXPIRED_DATE = "expires_date"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_TOKEN_TYPE = "token_type"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
    }

    override suspend fun initiateLoginFlow(): LoginFlowInit {
        return authenticationService.initializeLogin(
            LoginFlowRequest(
                AuthenticationService.CLIENT_ID,
                AuthenticationService.AUTH_CALLBACK,
                AuthenticationService.HANDLER
            )
        )
    }

    override suspend fun loginAuthentication(flowId: String, username: String, password: String): LoginFlowCreateEntry {
        return authenticationService.authenticate(
            AuthenticationService.AUTHENTICATE_BASE_PATH + flowId,
            LoginFlowAuthentication(
                AuthenticationService.CLIENT_ID,
                username,
                password
            )
        )
    }

    override suspend fun registerAuthorizationCode(authorizationCode: String) {
        authenticationService.getToken(
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
        val session = retrieveSession() ?: throw AuthorizationException()
        authenticationService.revokeToken(session.refreshToken, AuthenticationService.REVOKE_ACTION)
        saveSession(null)
        urlRepository.saveUrl("", true)
        urlRepository.saveUrl("", false)
        urlRepository.saveHomeWifiSsids(emptySet())
    }

    override suspend fun getSessionState(): SessionState {
        return if (retrieveSession() != null && urlRepository.getUrl() != null) {
            SessionState.CONNECTED
        } else {
            SessionState.ANONYMOUS
        }
    }

    override suspend fun buildAuthenticationUrl(callbackUrl: String): URL {
        val url = urlRepository.getUrl()

        return url!!.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("auth/authorize")
            .addEncodedQueryParameter("response_type", "code")
            .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
            .addEncodedQueryParameter("redirect_uri", callbackUrl)
            .build()
            .toUrl()
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
        val session = retrieveSession() ?: throw AuthorizationException()

        if (session.isExpired() || forceRefresh) {
            return authenticationService.refreshToken(
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

    override suspend fun isLockEnabled(): Boolean {
        return localStorage.getBoolean(PREF_BIOMETRIC_ENABLED)
    }
}
