package io.homeassistant.companion.android.onboarding.nameyourdevice

import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.util.isPubliclyAccessible
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

internal sealed interface NameYourDeviceNavigationEvent {

    /**
     * Represents the navigation event that is triggered when the device name is saved.
     *
     * @param serverId The ID of the server for which the device name was saved.
     * @param hasPlainTextAccess Boolean that defines if the server has a plain text URL.
     * @param isPubliclyAccessible Boolean that defines if the server is publicly accessible.
     */
    data class DeviceNameSaved(val serverId: Int, val hasPlainTextAccess: Boolean, val isPubliclyAccessible: Boolean) :
        NameYourDeviceNavigationEvent

    data class Error(@StringRes val messageRes: Int) : NameYourDeviceNavigationEvent
}

@HiltViewModel
internal class NameYourDeviceViewModel @VisibleForTesting constructor(
    private val route: NameYourDeviceRoute,
    private val serverManager: ServerManager,
    private val appVersionProvider: AppVersionProvider,
    private val messagingTokenProvider: MessagingTokenProvider,
    defaultName: String = Build.MODEL,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        serverManager: ServerManager,
        appVersionProvider: AppVersionProvider,
        messagingTokenProvider: MessagingTokenProvider,
    ) : this(
        savedStateHandle.toRoute<NameYourDeviceRoute>(),
        serverManager,
        appVersionProvider,
        messagingTokenProvider,
    )

    private val _navigationEventsFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    private val _deviceNameFlow = MutableStateFlow(defaultName)
    val deviceNameFlow = _deviceNameFlow.asStateFlow()

    private val _isValidNameFlow = MutableStateFlow(isValidName(deviceNameFlow.value))
    val isValidNameFlow = _isValidNameFlow.asStateFlow()
    private val _isSavingFlow = MutableStateFlow(false)
    val isSavingFlow = _isSavingFlow.asStateFlow()
    val isSaveClickableFlow = combine(isSavingFlow, isValidNameFlow) { isSaving, isValidName ->
        !isSaving && isValidName
    }

    fun onDeviceNameChange(name: String) {
        _deviceNameFlow.update { name }
        validateName(name)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            try {
                _isSavingFlow.emit(true)
                val serverId = addServer()
                val url = route.url
                val hasPlainTextAccess = url.startsWith("http://")

                enforceSecureConnectionIfNeeded(serverId = serverId, hasPlainTextAccess = hasPlainTextAccess)

                _navigationEventsFlow.emit(
                    NameYourDeviceNavigationEvent.DeviceNameSaved(
                        serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        isPubliclyAccessible = runCatching { URL(url).isPubliclyAccessible() }.getOrDefault(false),
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "Error while adding server")
                val messageRes = when {
                    e is HttpException && e.code() == 404 -> commonR.string.error_with_registration
                    e is SSLHandshakeException -> commonR.string.webview_error_FAILED_SSL_HANDSHAKE
                    e is SSLException -> commonR.string.webview_error_SSL_INVALID
                    else -> commonR.string.webview_error
                }
                _navigationEventsFlow.emit(NameYourDeviceNavigationEvent.Error(messageRes))
            } finally {
                _isSavingFlow.emit(false)
            }
        }
    }

    private fun validateName(name: String) {
        _isValidNameFlow.update { isValidName(name) }
    }

    private fun isValidName(name: String): Boolean {
        return name.isNotEmpty()
    }

    /**
     * Enforces secure connections for servers that were onboarded using HTTPS.
     *
     * If the server was accessed via HTTPS during onboarding, this function ensures that future
     * connections will only be allowed over secure channels to prevent future usage of HTTP.
     *
     * @param serverId The ID of the server to configure
     * @param hasPlainTextAccess Whether the server was accessed via HTTP during onboarding
     */
    private suspend fun enforceSecureConnectionIfNeeded(serverId: Int, hasPlainTextAccess: Boolean) {
        if (!hasPlainTextAccess) {
            // Until https://github.com/home-assistant/android/issues/6005 is not addressed this is going to fail.
            // It means that the flag allow insecure won't be set for HTTPS
            runCatching {
                serverManager.integrationRepository(serverId).setAllowInsecureConnection(false)
            }.onFailure { exception ->
                Timber.e(exception, "Failed to enforce secure connection for server $serverId")
            }
        }
    }

    private suspend fun addServer(): Int {
        val server = Server(
            _name = "",
            type = ServerType.TEMPORARY,
            connection = ServerConnectionInfo(
                externalUrl = route.url,
            ),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
        var tempServerId: Int? = null
        try {
            tempServerId = serverManager.addServer(server)
            serverManager.authenticationRepository(tempServerId).registerAuthorizationCode(route.authCode)
            serverManager.integrationRepository(tempServerId).registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    deviceNameFlow.value,
                    messagingTokenProvider(),
                ),
            )
            return serverManager.convertTemporaryServer(tempServerId)
                ?: throw IllegalStateException("Server still temporary")
        } catch (e: Exception) {
            // Fatal errors: if one of these calls fail, the app cannot proceed.
            // Show an error, clean up the session and require new registration.
            // Because this runs after the webview, the only expected errors are:
            // - missing mobile_app integration
            // - system version related in OkHttp (cryptography)
            // - general connection issues (offline/unknown)
            if (tempServerId != null) {
                Timber.e("Error while adding server and registering device. Reverting")
                runCatching { serverManager.authenticationRepository(tempServerId).revokeSession() }
                    .onFailure { Timber.e(it, "Failed to revoke session") }
                runCatching { serverManager.removeServer(tempServerId) }
                    .onFailure { Timber.e(it, "Failed to remove temporary server") }
            }
            throw e
        }
    }
}
