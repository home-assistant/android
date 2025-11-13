package io.homeassistant.companion.android.settings.wear.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Node
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsWearMainView : AppCompatActivity() {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    private val registerActivityResult = registerForActivityResult(
        OnboardApp(),
        this::onOnboardingComplete,
    )

    companion object {
        private var currentNodes = setOf<Node>()
        private var registerUrl: String? = null
        const val LANDING = "Landing"
        const val FAVORITES = "Favorites"
        const val TEMPLATES = "Templates"
        const val TEMPLATE_TILE = "Template/%s"

        fun newInstance(context: Context, wearNodes: Set<Node>, url: String?): Intent {
            currentNodes = wearNodes
            registerUrl = url
            return Intent(context, SettingsWearMainView::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeCompat()

        setContent {
            LoadSettingsHomeView(
                settingsWearViewModel,
                currentNodes.firstOrNull()?.displayName ?: "unknown",
                this::loginWearOs,
            ) { onBackPressedDispatcher.onBackPressed() }
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
                locationTrackingPossible = false,
                // While notifications are technically possible, the app can't handle this for the Wear device
                notificationsPossible = false,
                isWatch = true,
                discoveryOptions = OnboardApp.DiscoveryOptions.ADD_EXISTING_EXTERNAL,
                mayRequireTlsClientCertificate =
                (application as HomeAssistantApplication).keyChainRepository.getPrivateKey() != null,
            ),
        )
    }

    private fun onOnboardingComplete(result: OnboardApp.Output?) {
        result?.apply {
            settingsWearViewModel.sendAuthToWear(
                url,
                authCode,
                deviceName,
                deviceTrackingEnabled,
                true,
                tlsClientCertificateUri,
                tlsClientCertificatePassword,
            )
        } ?: Timber.e("onOnboardingComplete: Activity result returned null intent data")
    }
}
