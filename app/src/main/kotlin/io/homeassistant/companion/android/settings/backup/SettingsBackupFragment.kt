package io.homeassistant.companion.android.settings.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.settings.sensor.SensorSettingsFragment

/** Entry point for portable settings export and restore. */
@AndroidEntryPoint
class SettingsBackupFragment : Fragment() {
    private val viewModel: SettingsBackupViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HATheme {
                    SettingsBackupScreen(viewModel, onManageSensors = ::openSensors)
                }
            }
        }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.settings_backup)
    }

    private fun openSensors() {
        parentFragmentManager.commit {
            replace(R.id.content, SensorSettingsFragment::class.java, null)
            addToBackStack(getString(commonR.string.sensors))
        }
    }
}
