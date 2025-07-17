package io.homeassistant.companion.android.onboarding

import android.app.Application
import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantSearcher
import java.net.URL
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class OnboardingViewModel @Inject constructor(val serverManager: ServerManager, app: Application) :
    AndroidViewModel(app) {

    private val _homeAssistantSearcher = HomeAssistantSearcher(
        nsdManager = app.getSystemService()!!,
        wifiManager = app.getSystemService(),
        onStart = { discoveryActive = true },
        onInstanceFound = ::onInstanceFound,
        onError = { discoveryActive = false },
    )
    val homeAssistantSearcher: LifecycleObserver = _homeAssistantSearcher

    val foundInstances = mutableStateListOf<HomeAssistantInstance>()
    var discoveryActive by mutableStateOf(true)
        private set
    val manualUrl = mutableStateOf("")
    var discoveryOptions: OnboardApp.DiscoveryOptions? = null
    var manualContinueEnabled by mutableStateOf(false)
        private set
    var deviceIsWatch by mutableStateOf(false)
    val deviceName = mutableStateOf("")
    val locationTrackingPossible = mutableStateOf(false)
    var locationTrackingEnabled by mutableStateOf(false)
    val notificationsPossible = mutableStateOf(true)
    var notificationsEnabled by mutableStateOf(false)
    var mayRequireTlsClientCertificate by mutableStateOf(false)
    var tlsClientCertificateUri: Uri? by mutableStateOf(null)
    var tlsClientCertificateFilename by mutableStateOf("")
    var tlsClientCertificatePassword by mutableStateOf("")
    var tlsClientCertificatePasswordCorrect by mutableStateOf(false)

    private var authCode = ""

    fun onManualUrlUpdated(url: String) {
        manualUrl.value = url
        manualContinueEnabled = URLUtil.isValidUrl(url)
    }

    fun registerAuthCode(code: String) {
        authCode = code
    }

    fun onDeviceNameUpdated(name: String) {
        deviceName.value = name
    }

    fun setLocationTracking(enabled: Boolean) {
        locationTrackingEnabled = enabled
    }

    fun setNotifications(enabled: Boolean) {
        notificationsEnabled = enabled
    }

    fun getOutput() = OnboardApp.Output(
        url = manualUrl.value,
        authCode = authCode,
        deviceName = deviceName.value,
        deviceTrackingEnabled = locationTrackingEnabled,
        notificationsEnabled = notificationsEnabled,
        tlsClientCertificateUri = tlsClientCertificateUri?.toString() ?: "",
        tlsClientCertificatePassword = tlsClientCertificatePassword,
    )

    fun onDiscoveryActive() {
        if (discoveryOptions != OnboardApp.DiscoveryOptions.ADD_EXISTING_EXTERNAL || !foundInstances.isEmpty()) return

        serverManager.defaultServers.forEach {
            val url = it.connection.getUrl(isInternal = false) ?: return@forEach
            val version = it.version ?: return@forEach
            foundInstances.add(
                HomeAssistantInstance(
                    name = it.friendlyName,
                    url = url,
                    version = version,
                ),
            )
        }
    }

    private fun onInstanceFound(instance: HomeAssistantInstance) {
        if (
            (
                discoveryOptions == OnboardApp.DiscoveryOptions.ADD_EXISTING_EXTERNAL ||
                    discoveryOptions == OnboardApp.DiscoveryOptions.HIDE_EXISTING
                ) &&
            serverManager.defaultServers.any { it.connection.hasUrl(instance.url) }
        ) {
            // Skip anything with a URL known to the app, as it is added initially or should be hidden
            Timber.i("Skipping instance ${instance.name} (${instance.url}) because of option $discoveryOptions")
            return
        }

        if (foundInstances.none { it.url == instance.url }) foundInstances.add(instance)
    }

    override fun onCleared() {
        _homeAssistantSearcher.stopSearch()
    }

    private fun ServerConnectionInfo.hasUrl(url: URL): Boolean {
        val urls = listOf(internalUrl, externalUrl, cloudUrl)
        urls.forEach {
            if (it.isNullOrBlank()) return@forEach
            try {
                val parsed = URL(it)
                if (parsed.protocol == url.protocol && parsed.host == url.host && parsed.port == url.port) return true
            } catch (e: Exception) {
                // Unable to compare
            }
        }
        return false
    }
}
