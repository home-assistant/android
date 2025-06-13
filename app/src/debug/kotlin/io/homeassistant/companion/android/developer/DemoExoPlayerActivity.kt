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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.initializePlayer
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer

/**
 * Very basic demo of the ExoPlayer usage and the PlayerView.
 * It is useful to validate the look of the Player.
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
                        modifier = Modifier.size(width = 428.dp, height = 192.dp).align(Alignment.Center),
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

@Composable
private fun HAMediaPlayer(
    url: String,
    modifier: Modifier = Modifier,
    fullscreenModifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Inside,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<Player?>(null) }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
        // Initialize/release in onStart()/onStop() only because in a multi-window environment multiple
        // apps can be visible at the same time. The apps that are out-of-focus are paused, but video
        // playback should continue.
        LifecycleStartEffect(Unit) {
            player = initializePlayer(context)
            onStopOrDispose {
                releasePlayer()
            }
        }
    } else {
        // Call to onStop() is not guaranteed, hence we release the Player in onPause() instead
        LifecycleResumeEffect(Unit) {
            player = initializePlayer(context)
            onPauseOrDispose {
                releasePlayer()
            }
        }
    }

    player?.let { player ->
        DisposableEffect(player, url) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.playWhenReady = true
            player.prepare()
            onDispose {
                releasePlayer()
            }
        }
        HAMediaPlayer(
            player = player,
            contentScale = contentScale,
            modifier = modifier,
            fullscreenModifier = fullscreenModifier,
        )
    }
}
