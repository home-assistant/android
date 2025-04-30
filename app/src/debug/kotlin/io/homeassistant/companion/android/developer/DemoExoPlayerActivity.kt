package io.homeassistant.companion.android.developer

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.homeassistant.companion.android.R
import java.util.concurrent.Executors
import org.chromium.net.CronetEngine

/**
 * Very basic demo of the ExoPlayer usage and the PlayerView.
 * It is useful to validate the look of the Player since we override the default layout in [R.layout.exo_player_layout].
 *
 * It supports PIP mode.
 */
class DemoExoPlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private var isPipMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_demo_exo_player)
        playerView = findViewById<PlayerView>(R.id.exoplayerView)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Hide system UI
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onPause() {
        super.onPause()
        playerView.onPause()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        isPipMode = !isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
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

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(applicationContext).setMediaSourceFactory(
            DefaultMediaSourceFactory(
                CronetDataSource.Factory(
                    CronetEngine.Builder(applicationContext).enableQuic(true).build(),
                    Executors.newSingleThreadExecutor(),
                ),
            ).setLiveMaxSpeed(8.0f),
        ).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(
                0,
                30000,
                0,
                0,
            ).build(),
        ).build().apply {
            setMediaItem(MediaItem.fromUri("https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"))
            playWhenReady = true
            prepare()
        }

        playerView.player = exoPlayer
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
    }
}
