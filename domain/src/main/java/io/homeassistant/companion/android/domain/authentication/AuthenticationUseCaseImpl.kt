package io.homeassistant.companion.android.domain.authentication

import java.net.URL
import javax.inject.Inject

class AuthenticationUseCaseImpl @Inject constructor(
    private val authenticationRepository: AuthenticationRepository
) : AuthenticationUseCase {

    override suspend fun registerAuthorizationCode(authorizationCode: String) {
        authenticationRepository.registerAuthorizationCode(authorizationCode)
    }

    override suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String {
        return authenticationRepository.retrieveExternalAuthentication(forceRefresh)
    }

    override suspend fun revokeSession() {
        authenticationRepository.revokeSession()
    }

    override suspend fun getSessionState(): SessionState {
        return authenticationRepository.getSessionState()
    }

    override suspend fun buildAuthenticationUrl(callbackUrl: String): URL {
        return authenticationRepository.buildAuthenticationUrl(callbackUrl)
    }

    override suspend fun buildBearerToken(): String {
        return authenticationRepository.buildBearerToken()
    }

    override suspend fun setLockEnabled(enabled: Boolean) {
        return authenticationRepository.setLockEnabled(enabled)
    }

    override suspend fun isLockEnabled(): Boolean {
        return authenticationRepository.isLockEnabled()
    }
}
