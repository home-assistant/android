package io.homeassistant.companion.android.onboarding.manual

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class ManualSetupPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverManager: ServerManager
) : ManualSetupPresenter {
    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val view = context as ManualSetupView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var codeVerifier = CodeVerifier()
    private var authClient: RemoteAuthClient? = null

    override fun onNextClicked(context: Context, url: String) {
        view.showLoading()
        mainScope.launch {
            val request: OAuthRequest
            try {
                request = OAuthRequest.Builder(context)
                    .setAuthProviderUrl(
                        Uri.parse(UrlUtil.buildAuthenticationUrl(url))
                    )
                    .setCodeChallenge(CodeChallenge(codeVerifier))
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to build OAuthRequest", e)
                view.showError(commonR.string.failed_unsupported)
                return@launch
            }

            authClient = RemoteAuthClient.create(context)
            authClient?.let {
                view.showContinueOnPhone()
                it.sendAuthorizationRequest(
                    request,
                    Executors.newSingleThreadExecutor(),
                    object : RemoteAuthClient.Callback() {
                        override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                            Log.w(TAG, "Received authorization error for OAuth: $errorCode")
                            view.showError(
                                when (errorCode) {
                                    RemoteAuthClient.ERROR_UNSUPPORTED -> commonR.string.failed_unsupported
                                    RemoteAuthClient.ERROR_PHONE_UNAVAILABLE -> commonR.string.failed_phone_connection
                                    else -> commonR.string.failed_connection
                                }
                            )
                        }

                        override fun onAuthorizationResponse(
                            request: OAuthRequest,
                            response: OAuthResponse
                        ) {
                            response.responseUrl?.getQueryParameter("code")?.let { code ->
                                register(url, code)
                            } ?: run {
                                view.showError(commonR.string.failed_registration)
                            }
                        }
                    }
                )
            }
        }
    }

    fun register(url: String, code: String) {
        mainScope.launch {
            view.showLoading()
            var serverId: Int? = null

            try {
                val formattedUrl = UrlUtil.formattedUrlString(url)
                val server = Server(
                    _name = "",
                    type = ServerType.TEMPORARY,
                    connection = ServerConnectionInfo(
                        externalUrl = formattedUrl
                    ),
                    session = ServerSessionInfo(),
                    user = ServerUserInfo()
                )
                serverId = serverManager.addServer(server)
                serverManager.authenticationRepository(serverId).registerAuthorizationCode(code)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during registration", e)
                try {
                    if (serverId != null) {
                        serverManager.authenticationRepository(serverId).revokeSession()
                        serverManager.removeServer(serverId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Can't revoke session", e)
                }
                view.showError(commonR.string.failed_registration)
                return@launch
            }

            view.startIntegration(serverId)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
        authClient?.close()
    }
}
