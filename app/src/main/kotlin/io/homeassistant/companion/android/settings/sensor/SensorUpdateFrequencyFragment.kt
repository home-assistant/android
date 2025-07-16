package io.homeassistant.companion.android.settings.sensor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.SettingViewModel.Companion.DEFAULT_UPDATE_FREQUENCY
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.sensor.views.SensorUpdateFrequencyView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class SensorUpdateFrequencyFragment : Fragment() {

    val viewModel: SettingViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    // 0 is used for storing app level settings
                    val settingFlow = remember { viewModel.getSettingFlow(0) }
                    val settings = settingFlow.collectAsState(null)
                    SensorUpdateFrequencyView(
                        sensorUpdateFrequency = settings.value?.sensorUpdateFrequency ?: DEFAULT_UPDATE_FREQUENCY,
                        onSettingChanged = { viewModel.updateSensorSetting(0, it) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/core/sensors#android-sensors")
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.sensor_update_frequency)
    }
}
