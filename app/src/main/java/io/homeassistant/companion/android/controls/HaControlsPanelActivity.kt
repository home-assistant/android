package io.homeassistant.companion.android.controls

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.webview.WebViewActivity

@AndroidEntryPoint
class HaControlsPanelActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            WebViewActivity.newInstance(
                context = this,
                path = null,
                serverId = null
            )
        )
        finish()
        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }
}
