package io.homeassistant.companion.android.common.data.authentication

import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import java.net.URL

interface AuthenticationRepository {

    suspend fun initiateLoginFlow(): LoginFlowInit

    suspend fun loginAuthentication(flowId: String, username: String, password: String): LoginFlowCreateEntry

    suspend fun registerAuthorizationCode(authorizationCode: String)

    suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String

    suspend fun retrieveAccessToken(): String

    suspend fun revokeSession()

    suspend fun getSessionState(): SessionState

    suspend fun buildAuthenticationUrl(callbackUrl: String): URL

    suspend fun buildBearerToken(): String

    suspend fun setLockEnabled(enabled: Boolean)
    suspend fun isLockEnabled(): Boolean
}
