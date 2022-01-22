package io.homeassistant.companion.android.settings.wear.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.Node
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
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
        const val LANDING = "Landing"
        const val FAVORITES = "Favorites"
        const val TEMPLATE = "Template"

        fun newInstance(context: Context, wearNodes: Set<Node>): Intent {
            currentNodes = wearNodes
            return Intent(context, SettingsWearMainView::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoadSettingsHomeView(
                settingsWearViewModel,
                currentNodes.firstOrNull()?.displayName ?: "unknown",
                this::loginWearOs
            )
        }
    }

    private fun loginWearOs() {
        registerActivityResult.launch(
            OnboardApp.Input(
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
