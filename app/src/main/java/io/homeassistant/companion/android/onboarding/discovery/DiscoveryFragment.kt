package io.homeassistant.companion.android.onboarding.discovery

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DiscoveryFragment @Inject constructor() : Fragment() {

    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.onDiscoveryActive()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycle.addObserver(viewModel.homeAssistantSearcher)

        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    DiscoveryView(
                        discoveryActive = viewModel.discoveryActive,
                        foundInstances = viewModel.foundInstances,
                        manualSetupClicked = { navigateToManualSetup() },
                        instanceClicked = { onInstanceClicked(it, requireContext()) }
                    )
                }
            }
        }
    }

    private fun navigateToManualSetup() {
        parentFragmentManager
            .beginTransaction()
            .replace(R.id.content, ManualSetupFragment::class.java, null)
            .addToBackStack(null)
            .commit()
    }

    private fun onInstanceClicked(instance: HomeAssistantInstance, context: Context) {
        viewModel.manualUrl.value = instance.url.toString()
        val uri = OnboardingActivity.buildAuthUrl(context, viewModel.manualUrl.value)
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(uri))
    }
}
