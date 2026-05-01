package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme

@AndroidEntryPoint
class HealthConnectSettingsFragment : Fragment() {

    private val viewModel: HealthConnectSettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    HealthConnectSettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.health_connect_settings_title)
    }
}
