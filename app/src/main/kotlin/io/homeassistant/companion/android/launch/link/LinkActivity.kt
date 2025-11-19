package io.homeassistant.companion.android.launch.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.USE_NEW_LAUNCHER
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.launch.startLauncherOnboarding
import io.homeassistant.companion.android.launch.startLauncherWithNavigateTo
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LinkActivity : BaseActivity() {

    companion object {
        private const val EXTRA_URI = "EXTRA_URI"

        fun newInstance(context: Context, uri: Uri): Intent {
            return Intent(context, LinkActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
            }
        }
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var linkHandler: LinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We display the Icon of the app since this screen might be displayed when the user has to choose a server
        // before proceeding with the link.
        setContent {
            LinkActivityScreen()
        }

        val dataUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

        if (dataUri == null) {
            FailFast.fail { "Missing data in caller Intent" }
        } else {
            lifecycleScope.launch {
                when (val destination = linkHandler.handleLink(dataUri)) {
                    LinkDestination.NoDestination -> finish()
                    is LinkDestination.Onboarding -> {
                        if (USE_NEW_LAUNCHER) {
                            startLauncherOnboarding(
                                destination.serverUrl,
                                hideExistingServers = false,
                                skipWelcome = false,
                            )
                        } else {
                            startActivity(LaunchActivity.newInstance(this@LinkActivity, destination.serverUrl))
                        }
                        finish()
                    }

                    is LinkDestination.Webview -> {
                        navigateTo(destination.path, destination.serverId)
                    }
                }
            }
        }
    }

    private fun navigateTo(path: String, serverId: Int?) {
        val effectiveServerId = serverId ?: run {
            if (serverManager.defaultServers.size > 1) {
                openServerChooser(path)
                return
            }
            ServerManager.SERVER_ID_ACTIVE
        }

        if (USE_NEW_LAUNCHER) {
            startLauncherWithNavigateTo(path, effectiveServerId)
        } else {
            val intent = if (serverId != null) {
                WebViewActivity.newInstance(context = this, path = path, serverId = effectiveServerId)
            } else {
                WebViewActivity.newInstance(context = this, path = path)
            }
            startActivity(intent)
        }
        finish()
    }

    private fun openServerChooser(path: String) {
        supportFragmentManager.setFragmentResultListener(ServerChooserFragment.RESULT_KEY, this) { _, bundle ->
            if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                if (USE_NEW_LAUNCHER) {
                    startLauncherWithNavigateTo(path, bundle.getInt(ServerChooserFragment.RESULT_SERVER))
                } else {
                    startActivity(
                        WebViewActivity.newInstance(
                            context = this,
                            path = path,
                            serverId = bundle.getInt(ServerChooserFragment.RESULT_SERVER),
                        ),
                    )
                }
                finish()
            }
            supportFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
        }
        ServerChooserFragment().apply {
            // To avoid being stuck on an empty screen by mistake we make the dialog not cancelable.
            // The counterpart is that it forces the user to select a server.
            isCancelable = false
        }.show(supportFragmentManager, ServerChooserFragment.TAG)
    }
}

@Composable
@VisibleForTesting
fun LinkActivityScreen() {
    HomeAssistantAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .align(Alignment.Center),
            )
        }
    }
}

@Preview
@Composable
private fun LinkActivityScreenPreview() {
    LinkActivityScreen()
}
