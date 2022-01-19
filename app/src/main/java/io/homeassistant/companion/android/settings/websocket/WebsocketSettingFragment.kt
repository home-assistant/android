package io.homeassistant.companion.android.settings.websocket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.settings.websocket.views.WebsocketSettingView

class WebsocketSettingFragment : Fragment() {

    val viewModel: WebsocketSettingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    val settings = viewModel.getWebsocketSettingFlow(0)
                        .collectAsState(initial = viewModel.getWebsocketSetting(0))
                    WebsocketSettingView(
                        websocketSetting = settings.value.websocketSetting,
                        onSettingChanged = { viewModel.updateWebsocketSetting(0, it) }
                    )
                }
            }
        }
    }
}
