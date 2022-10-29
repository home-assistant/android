package io.homeassistant.companion.android.settings.sensor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.sensor.views.SensorUpdateFrequencyView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SensorUpdateFrequencyFragment : Fragment() {

    val viewModel: SettingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/core/sensors#android-sensors"))
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
                    val settings by viewModel.getSettingFlow(0)
                        .collectAsState(initial = viewModel.getSetting(0))
                    SensorUpdateFrequencyView(
                        sensorUpdateFrequencyBattery = settings.sensorUpdateFrequencyBattery,
                        sensorUpdateFrequencyPowered = settings.sensorUpdateFrequencyPowered,
                        onBatteryFrequencyChanged = { viewModel.updateSensorSetting(0, it, settings.sensorUpdateFrequencyPowered) },
                        onPoweredFrequencyChanged = { viewModel.updateSensorSetting(0, settings.sensorUpdateFrequencyBattery, it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.sensor_update_frequency)
    }
}
