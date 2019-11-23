package io.homeassistant.companion.android.data.authentication

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.authentication.SessionState
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.threeten.bp.Instant
import java.net.URL
import javax.inject.Inject
import javax.inject.Named


class AuthenticationRepositoryImpl @Inject constructor(
    private val authenticationService: AuthenticationService,
    @Named("session") private val localStorage: LocalStorage
) : AuthenticationRepository {

    companion object {
        private const val PREF_INSTANCES = "instances"
        private const val PREF_URL = "url"
    }

    override suspend fun saveUrl(url: URL) {
        addInstance(url)
        setCurrentInstance(url.toString())
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
                    Instant.now().epochSecond + it.expiresIn,
                    it.refreshToken!!,
                    it.tokenType
                )
            )
        }
    }

    override suspend fun retrieveExternalAuthentication(): String {
        return convertSession(ensureValidSession())
    }

    override suspend fun revokeSession() {
        val session = retrieveSession()
        session?.let {
            authenticationService.revokeToken(
                it.refreshToken,
                AuthenticationService.REVOKE_ACTION
            )
        }
        deleteInstance(getUrl().toString())
    }

    override suspend fun getSessionState(): SessionState {
        return if (retrieveSession() != null) {
            SessionState.CONNECTED
        } else {
            SessionState.ANONYMOUS
        }
    }

    override suspend fun getUrl(): URL? {
        return localStorage.getString(PREF_URL)?.toHttpUrlOrNull()?.toUrl()
    }

    override suspend fun buildAuthenticationUrl(callbackUrl: String): URL {
        val url = getUrl().toString()

        return url.toHttpUrl()
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

    override suspend fun getAllInstanceUrls(): List<String> {
        return getSessions().keys.toMutableList()
    }

    override suspend fun setCurrentInstance(url: String) {
        localStorage.putString(PREF_URL, url)
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
        System.out.println(getUrl().toString())
        System.out.println(getUrl()?.host)
        for (urlString in getSessions().keys) {
            System.out.println(urlString)
            if (urlString.toHttpUrlOrNull()?.host == getUrl()?.host) {
                return getSessions()[urlString]
            }
        }
        return null
    }

    private suspend fun getSessions(): MutableMap<String, Session?> {
        val instances = localStorage.getString(PREF_INSTANCES)
        val objectMapper = ObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return instances?.let {
            objectMapper.readValue<MutableMap<String, Session?>>(it)
        } ?: HashMap()
    }

    private suspend fun addInstance(url: URL, session: Session? = null) {
        var instanceMap: MutableMap<String, Session?> = getSessions()
        val objectMapper = ObjectMapper()
        var alreadyAdded = false
        for (stringUrl in instanceMap.keys) {
            if (stringUrl.toHttpUrlOrNull()?.host == url.host) {
                instanceMap[stringUrl] = session
                alreadyAdded = true
            }
        }
        if (!alreadyAdded) {
            instanceMap[url.toString()] = session
        }
        localStorage.putString(PREF_INSTANCES, objectMapper.writeValueAsString(instanceMap))
    }

    private suspend fun saveSessions(sessions: MutableMap<String, Session?>) {
        val objectMapper = ObjectMapper()
        localStorage.putString(PREF_INSTANCES, objectMapper.writeValueAsString(sessions))
    }

    private suspend fun ensureValidSession(): Session {
        val session = retrieveSession() ?: throw AuthorizationException()

        if (session.isExpired()) {
            return authenticationService.refreshToken(
                AuthenticationService.GRANT_TYPE_REFRESH,
                session.refreshToken,
                AuthenticationService.CLIENT_ID
            ).let {
                val refreshSession = Session(
                    it.accessToken,
                    Instant.now().epochSecond + it.expiresIn,
                    session.refreshToken,
                    it.tokenType
                )
                saveSession(refreshSession)
                refreshSession
            }
        }

        return session
    }

    private suspend fun saveSession(session: Session?) {
        session?.let {
            addInstance(getUrl()!!, session)
        }
    }

    override suspend fun deleteInstance(url: String) {
        val sessions = getSessions()
        sessions.remove(url)
        saveSessions(sessions)
    }

}