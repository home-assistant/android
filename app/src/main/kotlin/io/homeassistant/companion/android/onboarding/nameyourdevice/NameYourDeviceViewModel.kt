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
import io.homeassistant.companion.android.common.data.authentication.ServerRegistrationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
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

/**
 * ViewModel for the Name Your Device screen during phone/tablet onboarding.
 *
 * **Note:** This ViewModel is NOT used during Wear OS onboarding. The Wear onboarding flow
 * uses the screen without this view model since it handles device naming differently, as it returns the result
 * directly to the phone app via [io.homeassistant.companion.android.onboarding.WearOnboardApp.Output].
 */
@HiltViewModel
internal class NameYourDeviceViewModel @VisibleForTesting constructor(
    private val route: NameYourDeviceRoute,
    private val serverManager: ServerManager,
    private val serverRegistrationRepository: ServerRegistrationRepository,
    private val appVersionProvider: AppVersionProvider,
    private val messagingTokenProvider: MessagingTokenProvider,
    defaultName: String = Build.MODEL,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        serverManager: ServerManager,
        serverRegistrationRepository: ServerRegistrationRepository,
        appVersionProvider: AppVersionProvider,
        messagingTokenProvider: MessagingTokenProvider,
    ) : this(
        savedStateHandle.toRoute<NameYourDeviceRoute>(),
        serverManager,
        serverRegistrationRepository,
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
                val url = route.url
                val hasPlainTextAccess = url.startsWith("http://")
                val serverId = addServer(hasPlainTextAccess = hasPlainTextAccess)

                _navigationEventsFlow.emit(
                    NameYourDeviceNavigationEvent.DeviceNameSaved(
                        serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        isPubliclyAccessible = runCatching { URL(url).isPubliclyAccessible() }.getOrDefault(false),
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "Error while adding server")
                val messageRes = when (e) {
                    is HttpException if e.code() == 404 -> commonR.string.error_with_registration
                    is SSLHandshakeException -> commonR.string.webview_error_FAILED_SSL_HANDSHAKE
                    is SSLException -> commonR.string.webview_error_SSL_INVALID
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

    private suspend fun addServer(hasPlainTextAccess: Boolean): Int {
        var serverId: Int? = null
        try {
            val temporaryServer = checkNotNull(
                serverRegistrationRepository.registerAuthorizationCode(
                    url = route.url,
                    allowInsecureConnection = if (!hasPlainTextAccess) false else null,
                    authorizationCode = route.authCode,
                ),
            ) { "Registration failed" }

            serverId = serverManager.addServer(temporaryServer)
            serverManager.integrationRepository(serverId).registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    deviceNameFlow.value,
                    messagingTokenProvider(),
                ),
            )
            // Active the newly added server
            serverManager.activateServer(serverId)

            return serverId
        } catch (e: Exception) {
            // Fatal errors: if one of these calls fail, the app cannot proceed.
            // Show an error, clean up the session and require new registration.
            // Because this runs after the webview, the only expected errors are:
            // - missing mobile_app integration
            // - system version related in OkHttp (cryptography)
            // - general connection issues (offline/unknown)
            if (serverId != null) {
                Timber.e("Error while adding server and registering device. Reverting")
                runCatching { serverManager.authenticationRepository(serverId).revokeSession() }
                    .onFailure { Timber.e(it, "Failed to revoke session") }
                runCatching { serverManager.removeServer(serverId) }
                    .onFailure { Timber.e(it, "Failed to remove temporary server") }
            }
            throw e
        }
    }
}
