package io.homeassistant.companion.android.onboarding

import android.app.Application
import android.net.Uri
import android.net.nsd.NsdManager
import android.os.Build
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantSearcher
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.util.DisabledLocationHandler
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application,
    val authenticationRepository: AuthenticationRepository
) : AndroidViewModel(app) {

    private val homeAssistantSearcher = HomeAssistantSearcher(
        ContextCompat.getSystemService(app, NsdManager::class.java)!!,
        { instance ->
            foundInstances.add(instance)
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
    val deviceName = mutableStateOf(Build.MODEL)
    val locationTrackingEnabled = mutableStateOf(false)

    init {
        // start scanning for instances
        homeAssistantSearcher.beginSearch()
    }

    fun onManualUrlUpdated(url: String){
        manualUrl.value = url
        manualContinueEnabled.value = URLUtil.isValidUrl(url)
    }

    fun registerAuthCode(code: String) {
        authCode.value = code
    }

    override fun onCleared() {
        // stop scanning
        homeAssistantSearcher.stopSearch()
    }
}
