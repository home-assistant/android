package io.homeassistant.companion.android.settings.websocket

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.websocket.views.WebsocketSettingView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class WebsocketSettingFragment : Fragment() {

    val viewModel: SettingViewModel by viewModels()

    private var isIgnoringBatteryOptimizations by mutableStateOf(false)

    private val requestBackgroundAccessResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setIgnoringBatteryOptimizations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/notifications/notification-local"))
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
                    val settings = viewModel.getSettingFlow(0)
                        .collectAsState(initial = viewModel.getSetting(0))
                    WebsocketSettingView(
                        websocketSetting = settings.value.websocketSetting,
                        unrestrictedBackgroundAccess = isIgnoringBatteryOptimizations,
                        onSettingChanged = { viewModel.updateWebsocketSetting(0, it) },
                        onBackgroundAccessTapped = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestBackgroundAccessResult.launch(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${activity?.packageName}")
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.websocket_setting_name)
        setIgnoringBatteryOptimizations()
    }

    private fun setIgnoringBatteryOptimizations() {
        isIgnoringBatteryOptimizations = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
            context?.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(requireActivity().packageName)
                ?: false
    }
}
