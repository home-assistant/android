package io.homeassistant.companion.android.onboarding

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.onboarding.views.WelcomeView

class WelcomeFragment : Fragment() {

    companion object {
        fun newInstance(): WelcomeFragment {
            return WelcomeFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    WelcomeView(
                        onContinue = { welcomeNavigation() }
                    )
                }
            }
        }
    }

    private fun welcomeNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val discoveryFragment = DiscoveryFragment.newInstance()
            discoveryFragment.retainInstance = true
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, discoveryFragment)
                .addToBackStack("Welcome")
                .commit()
        } else {
            val manualFragment = ManualSetupFragment.newInstance()
            manualFragment.retainInstance = true
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, manualFragment)
                .addToBackStack("Welcome")
                .commit()
        }
    }
}
