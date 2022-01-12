package io.homeassistant.companion.android.onboarding

import android.app.Application
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantSearcher
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application,
    val authenticationRepository: AuthenticationRepository
) : AndroidViewModel(app) {

    private val homeAssistantSearcher = HomeAssistantSearcher(
        app.getSystemService()!!,
        { instance ->
            if (foundInstances.none { it.url == instance.url }) {
                foundInstances.add(instance)
            }
        },
        {
            Toast.makeText(app, R.string.failed_scan, Toast.LENGTH_LONG).show()
            // TODO: Go to manual setup?
        }
    )
    val foundInstances = mutableStateListOf<HomeAssistantInstance>()
    val manualUrl = mutableStateOf("")
    val manualContinueEnabled = mutableStateOf(false)
    val authCode = mutableStateOf("")
    val deviceName = mutableStateOf("")
    val locationTrackingPossible = mutableStateOf(false)
    val locationTrackingEnabled = mutableStateOf(false)

    fun onManualUrlUpdated(url: String) {
        manualUrl.value = url
        manualContinueEnabled.value = URLUtil.isValidUrl(url)
    }

    fun registerAuthCode(code: String) {
        authCode.value = code
    }

    fun onDeviceNameUpdated(name: String) {
        deviceName.value = name
    }

    fun startSearch() {
        homeAssistantSearcher.beginSearch()
    }

    fun stopSearch() {
        homeAssistantSearcher.stopSearch()
    }

    override fun onCleared() {
        stopSearch()
    }
}
