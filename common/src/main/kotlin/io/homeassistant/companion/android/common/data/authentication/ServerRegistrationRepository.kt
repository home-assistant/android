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

class ServerRegistrationRepository @Inject constructor(
    private val authenticationService: AuthenticationService,
    @NamedInstallId private val installId: SuspendProvider<String>,
) {

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
