package io.homeassistant.companion.android.settings.websocket

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
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
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.websocket.views.WebsocketSettingView
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class WebsocketSettingFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    @Inject
    lateinit var wifiHelper: WifiHelper

    val viewModel: SettingViewModel by viewModels()

    private var isIgnoringBatteryOptimizations by mutableStateOf(false)

    private val requestBackgroundAccessResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setIgnoringBatteryOptimizations()
    }

    private var serverId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            serverId = it.getInt(EXTRA_SERVER, serverId)
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
                    val settings = viewModel.getSettingFlow(serverId)
                        .collectAsState(initial = viewModel.getSetting(serverId))
                    WebsocketSettingView(
                        websocketSetting = settings.value.websocketSetting,
                        unrestrictedBackgroundAccess = isIgnoringBatteryOptimizations,
                        hasWifi = wifiHelper.hasWifi(),
                        onSettingChanged = { viewModel.updateWebsocketSetting(serverId, it) },
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/notifications/notification-local")
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
