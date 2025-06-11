package io.homeassistant.companion.android.developer

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

/**
 * Very basic demo of the ExoPlayer usage and the PlayerView.
 * It is useful to validate the look of the Player since we override the default layout in [R.layout.exo_player_layout].
 *
 * It supports PIP mode.
 */
class DemoExoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeAssistantAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HAMediaPlayer(
                        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                        modifier = Modifier.size(256.dp, 128.dp).align(Alignment.Center),
                    )
                }
            }
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Hide system UI
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build(),
        )
    }
}
