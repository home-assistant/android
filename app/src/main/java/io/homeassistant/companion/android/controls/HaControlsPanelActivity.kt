package io.homeassistant.companion.android.controls

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HaControlsPanelActivity : AppCompatActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!serverManager.isRegistered()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val serverId = prefsRepository.getControlsPanelServer() ?: serverManager.getServer()?.id
            val path = prefsRepository.getControlsPanelPath()
            Log.d("HaControlsPanel", "Launching WebView…")
            startActivity(
                WebViewActivity.newInstance(
                    context = this@HaControlsPanelActivity,
                    path = path,
                    serverId = serverId
                ).apply {
                    putExtra(WebViewActivity.EXTRA_SHOW_WHEN_LOCKED, true)
                }
            )
            overridePendingTransition(0, 0) // Disable activity start/stop animation

            // The device controls panel can flicker if this activity finishes to quickly, so handle
            // it in onPause instead to reduce this
            launched = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (launched) finish()
    }
}
