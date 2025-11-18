package io.homeassistant.companion.android.settings.websocket

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.util.isIgnoringBatteryOptimizations
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.SettingViewModel.Companion.DEFAULT_WEBSOCKET_SETTING
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.websocket.views.WebsocketSettingView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject

@AndroidEntryPoint
class WebsocketSettingFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    @Inject
    lateinit var wifiHelper: WifiHelper

    val viewModel: SettingViewModel by viewModels()

    private var isIgnoringBatteryOptimizations by mutableStateOf(false)

    private val requestBackgroundAccessResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setIgnoringBatteryOptimizations()
        }

    private var serverId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            serverId = it.getInt(EXTRA_SERVER, serverId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    val settingFlow = remember { viewModel.getSettingFlow(serverId) }
                    val settings = settingFlow.collectAsState(initial = null)
                    WebsocketSettingView(
                        websocketSetting = settings.value?.websocketSetting ?: DEFAULT_WEBSOCKET_SETTING,
                        unrestrictedBackgroundAccess = isIgnoringBatteryOptimizations,
                        hasWifi = wifiHelper.hasWifi(),
                        onSettingChanged = { viewModel.updateWebsocketSetting(serverId, it) },
                        onBackgroundAccessTapped = {
                            requestBackgroundAccessResult.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    "package:${activity?.packageName}".toUri(),
                                ),
                            )
                        },
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
        isIgnoringBatteryOptimizations = context?.isIgnoringBatteryOptimizations() == true
    }
}
