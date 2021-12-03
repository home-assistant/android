package io.homeassistant.companion.android.onboarding

import android.app.Application
import android.net.nsd.NsdManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantSearcher
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application
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

    init {
        // start scanning for instances
        homeAssistantSearcher.beginSearch()
    }

    override fun onCleared() {
        // stop scanning
        homeAssistantSearcher.stopSearch()
    }
}
