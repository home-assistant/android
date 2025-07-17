package io.homeassistant.companion.android.onboarding.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class ManualSetupFragment : Fragment() {

    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ManualSetupView(
                        manualUrl = viewModel.manualUrl,
                        onManualUrlUpdated = viewModel::onManualUrlUpdated,
                        manualContinueEnabled = viewModel.manualContinueEnabled,
                        connectedClicked = { connectClicked() },
                    )
                }
            }
        }
    }

    private fun connectClicked() {
        parentFragmentManager
            .beginTransaction()
            .replace(R.id.content, AuthenticationFragment::class.java, null)
            .addToBackStack(null)
            .commit()
    }
}
