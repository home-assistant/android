package io.homeassistant.companion.android.onboarding.manual

import android.content.Context
import androidx.core.net.toUri
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.ServerRegistrationRepository
import io.homeassistant.companion.android.util.UrlUtil
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class ManualSetupPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverRegistrationRepository: ServerRegistrationRepository,
) : ManualSetupPresenter {

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
                        UrlUtil.buildAuthenticationUrl(url).toUri(),
                    )
                    .setCodeChallenge(CodeChallenge(codeVerifier))
                    .build()
            } catch (e: Exception) {
                Timber.e(e, "Unable to build OAuthRequest")
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
                            Timber.w("Received authorization error for OAuth: $errorCode")
                            view.showError(
                                when (errorCode) {
                                    RemoteAuthClient.ERROR_UNSUPPORTED -> commonR.string.failed_unsupported
                                    RemoteAuthClient.ERROR_PHONE_UNAVAILABLE -> commonR.string.failed_phone_connection
                                    else -> commonR.string.failed_connection
                                },
                            )
                        }

                        override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                            response.responseUrl?.getQueryParameter("code")?.let { code ->
                                register(url, code)
                            } ?: run {
                                view.showError(commonR.string.failed_registration)
                            }
                        }
                    },
                )
            }
        }
    }

    fun register(url: String, code: String) {
        mainScope.launch {
            view.showLoading()
            val temporaryServer = try {
                checkNotNull(
                    serverRegistrationRepository.registerAuthorizationCode(
                        url,
                        code,
                        null,
                    ),
                ) { "Registration failed" }
            } catch (e: Exception) {
                Timber.e(e, "Exception during registration")
                view.showError(commonR.string.failed_registration)
                return@launch
            }

            view.startIntegration(temporaryServer)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
        authClient?.close()
    }
}
