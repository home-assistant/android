package io.homeassistant.companion.android.controls

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.os.Bundle
import android.service.controls.ControlsProviderService
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class HaControlsPanelActivity : AppCompatActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private var launched = false

    @SuppressLint("InlinedApi") // This activity will only be launched on Android 14+
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        lifecycleScope.launch {
            if (!serverManager.isRegistered()) {
                finish()
            }
        }

        val disallowLocked =
            intent?.hasExtra(ControlsProviderService.EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS) != true ||
                intent?.getBooleanExtra(ControlsProviderService.EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS, false) != true
        val keyguardManager = getSystemService<KeyguardManager>()
        val isLocked = keyguardManager?.isKeyguardLocked ?: true
        if (disallowLocked && isLocked) {
            setContent { LockedPanelView() }
            return
        }

        lifecycleScope.launch {
            val serverId = prefsRepository.getControlsPanelServer() ?: serverManager.getServer()?.id
            val path = prefsRepository.getControlsPanelPath()
            Timber.d("Launching WebView…")
            startActivity(
                WebViewActivity.newInstance(
                    context = this@HaControlsPanelActivity,
                    path = path,
                    serverId = serverId,
                ).apply {
                    putExtra(WebViewActivity.EXTRA_SHOW_WHEN_LOCKED, true)
                },
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

    @Composable
    fun LockedPanelView() {
        HomeAssistantAppTheme {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(commonR.string.tile_auth_required),
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
