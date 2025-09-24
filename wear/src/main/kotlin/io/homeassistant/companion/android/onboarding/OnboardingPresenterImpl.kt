package io.homeassistant.companion.android.onboarding

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.util.UrlUtil
import java.net.URL
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
class OnboardingPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverManager: ServerManager,
) : OnboardingPresenter {

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

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Timber.d("onDataChanged: [${dataEvents.count}]")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        Timber.d("onDataChanged: found home_assistant_instance")
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
                getString("version", ""),
            )
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
                        externalUrl = formattedUrl,
                    ),
                    session = ServerSessionInfo(),
                    user = ServerUserInfo(),
                )
                serverId = serverManager.addServer(server)
                serverManager.authenticationRepository(serverId).registerAuthorizationCode(code)
            } catch (e: Exception) {
                Timber.e(e, "Exception during registration")
                try {
                    if (serverId != null) {
                        serverManager.authenticationRepository(serverId).revokeSession()
                        serverManager.removeServer(serverId)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Can't revoke session")
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
