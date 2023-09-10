package io.homeassistant.companion.android.controls

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!serverManager.isRegistered()) return

        lifecycleScope.launch {
            val serverId = prefsRepository.getControlsPanelServer() ?: serverManager.getServer()?.id
            val path = prefsRepository.getControlsPanelPath()
            startActivity(
                WebViewActivity.newInstance(
                    context = this@HaControlsPanelActivity,
                    path = path,
                    serverId = serverId
                ).apply {
                    putExtra(WebViewActivity.EXTRA_SHOW_WHEN_LOCKED, true)
                }
            )
            finish()
        }
    }
}
