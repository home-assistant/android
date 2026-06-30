package io.homeassistant.companion.android.launch.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.startLaunchInvitation
import io.homeassistant.companion.android.launch.startLaunchWithNavigateTo
import io.homeassistant.companion.android.settings.server.ServerChooser
import io.homeassistant.companion.android.settings.server.ServerChooserItem
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

    private val viewModel: LinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We display the Icon of the app since this screen might be displayed when the user has to choose a server
        // before proceeding with the link.
        setContent {
            HATheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LinkActivityScreen(
                    uiState = uiState,
                    onServerSelected = viewModel::onServerSelected,
                    onServerChooserDismissed = viewModel::onServerChooserDismissed,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvents.collect(::handleNavigationEvent)
            }
        }

        // The destination is resolved once per launch. On recreation the destination is already
        // reflected in the ViewModel state, so we must not re-handle the intent.
        if (savedInstanceState == null) {
            viewModel.onLinkReceived(intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data)
        }
    }

    private fun handleNavigationEvent(event: LinkNavigationEvent) {
        when (event) {
            LinkNavigationEvent.Finish -> Unit
            is LinkNavigationEvent.OpenInvitation -> startLaunchInvitation(event.serverUrl)
            is LinkNavigationEvent.NavigateToWebView -> startLaunchWithNavigateTo(event.target, event.serverId)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@VisibleForTesting
fun LinkActivityScreen(
    uiState: LinkUiState,
    onServerSelected: (Int) -> Unit,
    onServerChooserDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .align(Alignment.Center),
        )

        if (uiState is LinkUiState.ChoosingServer) {
            ServerChooser(
                items = uiState.items,
                onServerSelected = onServerSelected,
                onDismissRequest = onServerChooserDismissed,
            )
        }
    }
}

@Preview
@Composable
private fun LinkActivityScreenPreview() {
    HAThemeForPreview {
        LinkActivityScreen(
            uiState = LinkUiState.ChoosingServer(
                items = listOf(
                    ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home"),
                    ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
                ),
                target = FrontendTarget.Path("/lovelace"),
            ),
            onServerSelected = {},
            onServerChooserDismissed = {},
        )
    }
}
