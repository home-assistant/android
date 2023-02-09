package io.homeassistant.companion.android.onboarding

import android.app.Application
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantSearcher
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application
) : AndroidViewModel(app) {

    private val _homeAssistantSearcher = HomeAssistantSearcher(
        nsdManager = app.getSystemService()!!,
        wifiManager = app.getSystemService(),
        onInstanceFound = { instance ->
            if (foundInstances.none { it.url == instance.url }) {
                foundInstances.add(instance)
            }
        },
        onError = {
            Toast.makeText(app, R.string.failed_scan, Toast.LENGTH_LONG).show()
            // TODO: Go to manual setup?
        }
    )
    val homeAssistantSearcher: LifecycleObserver = _homeAssistantSearcher

    val foundInstances = mutableStateListOf<HomeAssistantInstance>()
    val manualUrl = mutableStateOf("")
    var manualContinueEnabled by mutableStateOf(false)
        private set
    var authCode by mutableStateOf("")
        private set
    var deviceIsWatch by mutableStateOf(false)
    val deviceName = mutableStateOf("")
    val locationTrackingPossible = mutableStateOf(false)
    var locationTrackingEnabled by mutableStateOf(false)
    val notificationsPossible = mutableStateOf(true)
    var notificationsEnabled by mutableStateOf(false)

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
        notificationsEnabled = notificationsEnabled
    )

    override fun onCleared() {
        _homeAssistantSearcher.stopSearch()
    }
}
