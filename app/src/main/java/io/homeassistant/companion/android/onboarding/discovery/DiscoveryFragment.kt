package io.homeassistant.companion.android.onboarding.discovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class DiscoveryFragment @Inject constructor() : Fragment() {

    companion object {

        private const val TAG = "DiscoveryFragment"
        private const val HOME_ASSISTANT = "https://www.home-assistant.io"
    }

    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        lifecycle.addObserver(viewModel.homeAssistantSearcher)

        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    DiscoveryView(
                        onboardingViewModel = viewModel,
                        whatIsThisClicked = { openHomeAssistantHomePage() },
                        manualSetupClicked = { navigateToManualSetup() },
                        instanceClicked = { onInstanceClicked(it) }
                    )
                }
            }
        }
    }

    private fun openHomeAssistantHomePage() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(HOME_ASSISTANT)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to load Home Assistant home page", e)
            Toast.makeText(context, commonR.string.what_is_this_crash, Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToManualSetup() {
        parentFragmentManager
            .beginTransaction()
            .replace(R.id.content, ManualSetupFragment::class.java, null)
            .addToBackStack(null)
            .commit()
    }

    private fun onInstanceClicked(instance: HomeAssistantInstance) {
        viewModel.manualUrl.value = instance.url.toString()
        parentFragmentManager
            .beginTransaction()
            .replace(R.id.content, AuthenticationFragment::class.java, null)
            .addToBackStack(null)
            .commit()
    }
}
