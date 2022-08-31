package io.homeassistant.companion.android.settings.wear.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Node
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsWearMainView : AppCompatActivity() {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val registerActivityResult = registerForActivityResult(
        OnboardApp(),
        this::onOnboardingComplete
    )

    companion object {
        private const val TAG = "SettingsWearDevice"
        private var currentNodes = setOf<Node>()
        private var registerUrl: String? = null
        const val LANDING = "Landing"
        const val FAVORITES = "Favorites"
        const val TEMPLATE = "Template"

        fun newInstance(context: Context, wearNodes: Set<Node>, url: String?): Intent {
            currentNodes = wearNodes
            registerUrl = url
            return Intent(context, SettingsWearMainView::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoadSettingsHomeView(
                settingsWearViewModel,
                currentNodes.firstOrNull()?.displayName ?: "unknown",
                this::loginWearOs,
                this::onBackPressed
            )
        }

        if (registerUrl != null) {
            lifecycleScope.launch {
                settingsWearViewModel.hasData.collect { hasData ->
                    if (hasData) {
                        if (!settingsWearViewModel.isAuthenticated.value) loginWearOs()
                        this@launch.cancel() // Stop listening, we only need initial load
                    }
                }
            }
        }
    }

    private fun loginWearOs() {
        registerActivityResult.launch(
            OnboardApp.Input(
                url = registerUrl,
                defaultDeviceName = currentNodes.firstOrNull()?.displayName ?: "unknown",
                locationTrackingPossible = false
            )
        )
    }

    private fun onOnboardingComplete(result: OnboardApp.Output?) {
        if (result != null) {
            val (url, authCode, deviceName, deviceTrackingEnabled) = result
            settingsWearViewModel.sendAuthToWear(url, authCode, deviceName, deviceTrackingEnabled)
        } else
            Log.e(TAG, "onOnboardingComplete: Activity result returned null intent data")
    }
}
