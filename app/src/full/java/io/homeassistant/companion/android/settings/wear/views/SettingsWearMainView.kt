package io.homeassistant.companion.android.settings.wear.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.Node
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SettingsWearMainView : AppCompatActivity() {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val registerActivityResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            this::onOnboardingComplete
        )

    companion object {
        private const val TAG = "SettingsWearDevice"
        private var currentNodes = setOf<Node>()
        const val LANDING = "Landing"
        const val FAVORITES = "Favorites"

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
                currentNodes.first().displayName,
                this::loginWearOs
            )
        }
    }

    private fun loginWearOs() {
        registerActivityResult.launch(OnboardingActivity.newInstance(this, currentNodes.first().displayName, false))
    }

    private fun onOnboardingComplete(result: ActivityResult) {
        val intent = result.data!!
        val url = intent.getStringExtra("URL").toString()
        val authCode = intent.getStringExtra("AuthCode").toString()
        val deviceName = intent.getStringExtra("DeviceName").toString()
        val deviceTrackingEnabled = intent.getBooleanExtra("LocationTracking", false)
        settingsWearViewModel.sendAuthToWear(url, authCode, deviceName, deviceTrackingEnabled)
    }
}
