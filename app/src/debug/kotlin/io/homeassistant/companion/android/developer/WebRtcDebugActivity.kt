package io.homeassistant.companion.android.developer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.settings.developer.webrtc.WebRtcDebugFragment

/**
 * Debug-only shortcut to the WebRTC camera player debug screen, so it can be reached from the
 * dev playground without navigating through the settings.
 */
@AndroidEntryPoint
class WebRtcDebugActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, WebRtcDebugFragment())
                .commit()
        }
    }
}
