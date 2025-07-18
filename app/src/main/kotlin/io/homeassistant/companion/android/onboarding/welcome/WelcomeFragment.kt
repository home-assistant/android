package io.homeassistant.companion.android.onboarding.welcome

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    WelcomeView(
                        onContinue = { welcomeNavigation() },
                    )
                }
            }
        }
    }

    private fun welcomeNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, DiscoveryFragment::class.java, null)
                .addToBackStack("Welcome")
                .commit()
        } else {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, ManualSetupFragment::class.java, null)
                .addToBackStack("Welcome")
                .commit()
        }
    }
}
