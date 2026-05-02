package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.sensors.HealthConnectSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HealthConnectSettingsFragment : Fragment() {

    private val viewModel: HealthConnectSettingsViewModel by viewModels()

    /**
     * Activity-result launcher for the Health Connect bulk permission contract. Owned by
     * the fragment because the contract requires an Activity host; the view-model emits
     * the perm set on a SharedFlow which we observe and forward to this launcher.
     */
    private var permissionsLauncher: ActivityResultLauncher<Set<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HealthConnectSensorManager.getPermissionResultContract()?.let { contract ->
            permissionsLauncher = registerForActivityResult(contract) {
                // Trigger an immediate sensor refresh so newly-granted READ perms start
                // pulling values right away instead of waiting for the next 15-min cycle.
                SensorReceiver.updateAllSensors(requireContext())
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    HealthConnectSettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enableAllRequested.collect { perms ->
                    permissionsLauncher?.launch(perms)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.health_connect_settings_title)
    }
}
