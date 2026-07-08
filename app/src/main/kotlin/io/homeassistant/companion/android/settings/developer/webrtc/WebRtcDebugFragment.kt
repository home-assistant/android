package io.homeassistant.companion.android.settings.developer.webrtc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.developer.webrtc.views.WebRtcDebugView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class WebRtcDebugFragment : Fragment() {

    private val viewModel: WebRtcDebugViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    val session by viewModel.session.collectAsStateWithLifecycle()
                    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
                    WebRtcDebugView(
                        player = session,
                        playerState = playerState,
                        eglContext = viewModel.eglContext,
                        onStart = viewModel::startSession,
                        onStop = viewModel::stopSession,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.webrtc_debug)
    }
}
