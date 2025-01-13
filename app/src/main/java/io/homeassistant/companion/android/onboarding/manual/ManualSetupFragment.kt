package io.homeassistant.companion.android.onboarding.manual

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class ManualSetupFragment : Fragment() {

    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ManualSetupView(
                        manualUrl = viewModel.manualUrl,
                        onManualUrlUpdated = viewModel::onManualUrlUpdated,
                        manualContinueEnabled = viewModel.manualContinueEnabled,
                        connectedClicked = { connectClicked(requireContext(), viewModel.manualUrl.value) }
                    )
                }
            }
        }
    }

    private fun connectClicked(context: Context, url: String) {
        val uri = OnboardingActivity.buildAuthUrl(context, url)
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(uri))
    }
}
