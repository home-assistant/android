package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URL
import java.util.concurrent.Executors
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class OnboardingPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
) : OnboardingPresenter {
    companion object {
        private const val TAG = "OnboardingPresenter"
    }

    private val view = context as OnboardingView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var codeVerifier = CodeVerifier()
    private var authClient: RemoteAuthClient? = null

    override fun onInstanceClickedWithoutApp(context: Context, url: String) {
        // This is very unlikely to happen, as instances are usually only discovered by the app.
        // Still, the connection might be lost which is why this exists. Based on onNextClicked in
        // ManualSetupPresenterImpl. Also a good starting point for manual URL if it is possible to
        // enter this on the device without the app in the future.
        mainScope.launch {
            val request = OAuthRequest.Builder(context)
                .setAuthProviderUrl(
                    Uri.parse(
                        authenticationUseCase.buildAuthenticationUrl(
                            url,
                            OAuthRequest.WEAR_REDIRECT_URL_PREFIX + BuildConfig.APPLICATION_ID
                        )
                    )
                )
                .setCodeChallenge(CodeChallenge(codeVerifier))
                .build()

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

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: [${dataEvents.count}]")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        Log.d(TAG, "onDataChanged: found home_assistant_instance")
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceFound(instance)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceLost(instance)
                    }
                }
            }
        }
        dataEvents.release()
    }

    override fun getInstance(map: DataMap): HomeAssistantInstance {
        map.apply {
            return HomeAssistantInstance(
                getString("name", ""),
                URL(getString("url", "")),
                getString("version", "")
            )
        }
    }

    fun register(url: String, code: String) {
        mainScope.launch {
            view.showLoading()

            try {
                urlUseCase.saveUrl(url)
                authenticationUseCase.registerAuthorizationCode(code)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during registration", e)
                view.showError(commonR.string.failed_registration)
                return@launch
            }

            view.startIntegration()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
        authClient?.close()
    }
}
