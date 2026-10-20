package io.homeassistant.companion.android.developer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.developer.webrtc.WebRtcDebug
import io.homeassistant.companion.android.developer.webrtc.WebRtcDebugViewModel
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat

/**
 * Debug-only playground entry that exercises the native WebRTC camera player against a camera
 * entity of a chosen server, without any dashboard or frontend integration.
 */
@AndroidEntryPoint
class WebRtcDebugActivity : AppCompatActivity() {

    private val viewModel: WebRtcDebugViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        setContent {
            HATheme {
                Scaffold(contentWindowInsets = WindowInsets.safeContent) { padding ->
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val session by viewModel.session.collectAsStateWithLifecycle()
                    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                    val micState by viewModel.micState.collectAsStateWithLifecycle()
                    val stats by viewModel.stats.collectAsStateWithLifecycle()
                    WebRtcDebug(
                        state = state,
                        session = session,
                        connectionState = connectionState,
                        micState = micState,
                        stats = stats,
                        onServerSelected = viewModel::selectServerId,
                        onEntitySelected = viewModel::selectEntityId,
                        onMediaOptionsChange = viewModel::updateMediaOptions,
                        onStart = viewModel::startSession,
                        onStop = viewModel::stopSession,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
