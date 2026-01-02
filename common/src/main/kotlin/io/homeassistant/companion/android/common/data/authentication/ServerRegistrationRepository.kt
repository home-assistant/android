package io.homeassistant.companion.android.common.data.authentication

import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService.Companion.SEGMENT_AUTH_TOKEN
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.TemporaryServer
import io.homeassistant.companion.android.di.qualifiers.NamedInstallId
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * Handles the registration of new Home Assistant servers by exchanging OAuth authorization
 * codes.
 */
class ServerRegistrationRepository @Inject constructor(
    private val authenticationService: AuthenticationService,
    @param:NamedInstallId private val installId: SuspendProvider<String>,
) {

    /**
     * Exchanges an OAuth authorization code for access and refresh tokens, creating a temporary
     * server configuration that can be used to complete the onboarding process.
     *
     * @param url The base URL of the Home Assistant server (e.g., "https://homeassistant.local:8123").
     * @param authorizationCode The OAuth authorization code received from the authentication flow.
     * @param allowInsecureConnection Whether to allow insecure connections (null to let this handle later in the flow).
     * @return A [TemporaryServer] containing the session tokens and server URL if successful,
     *         or `null` if the URL is invalid or the token response is missing a refresh token.
     */
    suspend fun registerAuthorizationCode(
        url: String,
        authorizationCode: String,
        allowInsecureConnection: Boolean?,
    ): TemporaryServer? {
        return url.toHttpUrlOrNull()?.let { httpUrl ->
            authenticationService.getToken(
                httpUrl.newBuilder().addPathSegments(SEGMENT_AUTH_TOKEN).build(),
                AuthenticationService.GRANT_TYPE_CODE,
                authorizationCode,
                AuthenticationService.CLIENT_ID,
            ).let {
                if (it.refreshToken == null) {
                    Timber.e("Missing refresh token in the retrieved token")
                    null
                } else {
                    TemporaryServer(
                        externalUrl = url,
                        allowInsecureConnection = allowInsecureConnection,
                        session = ServerSessionInfo(
                            accessToken = it.accessToken,
                            refreshToken = it.refreshToken,
                            tokenExpiration = System.currentTimeMillis() / 1000 + it.expiresIn,
                            tokenType = it.tokenType,
                            installId = installId(),
                        ),
                    )
                }
            }
        } ?: run {
            Timber.e("No URL available to register auth code")
            null
        }
    }
}
