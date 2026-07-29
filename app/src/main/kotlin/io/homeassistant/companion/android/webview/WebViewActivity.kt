package io.homeassistant.companion.android.webview

import android.os.Bundle
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.startLaunchWithNavigateTo

private const val EXTRA_PATH = "path"
private const val EXTRA_SERVER = "server"

@Deprecated(
    "WebViewActivity must not be used; kept only to redirect legacy shortcuts.",
    level = DeprecationLevel.ERROR,
)
class WebViewActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        FailFast.fail { "WebViewActivity must not be used anymore." }
        super.onCreate(savedInstanceState)

        // Legacy shortcuts launch this activity with a path and/or server extra; redirect them to the
        // navigate deep link so they open in FrontendScreen. Shortcuts are rewritten to the deep-link
        // format by the startup migration in HomeAssistantApplication; this only covers direct launches
        // that slip through.
        if (intent.hasExtra(EXTRA_PATH) || intent.hasExtra(EXTRA_SERVER)) {
            val target = intent.getStringExtra(EXTRA_PATH)?.let(FrontendTarget::fromRawPath) ?: FrontendTarget.Default
            val serverId = intent.getIntExtra(EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE)
            startLaunchWithNavigateTo(target, serverId)
            finish()
            return
        }
    }
}
