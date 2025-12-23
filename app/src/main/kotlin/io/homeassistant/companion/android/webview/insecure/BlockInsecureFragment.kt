package io.homeassistant.companion.android.webview.insecure

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.URL_SECURITY_LEVEL_DOCUMENTATION
import io.homeassistant.companion.android.settings.ConnectionSecurityLevelFragment
import io.homeassistant.companion.android.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Fragment explaining why the current connection is blocked.
 */
@AndroidEntryPoint
class BlockInsecureFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "block_insecure_result"
        private const val EXTRA_SERVER = "server_id"

        fun newInstance(serverId: Int): BlockInsecureFragment {
            return BlockInsecureFragment().apply {
                arguments = Bundle().apply {
                    putInt(EXTRA_SERVER, serverId)
                }
            }
        }
    }

    private val serverId: Int
        get() = arguments?.getInt(EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE) ?: ServerManager.SERVER_ID_ACTIVE

    private val viewModel: BlockInsecureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<BlockInsecureViewModelFactory> { factory ->
                factory.create(serverId)
            }
        },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refresh()
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                val uriHandler = LocalUriHandler.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                HATheme {
                    BlockInsecureScreen(
                        missingHomeSetup = uiState.missingHomeSetup,
                        missingLocation = uiState.missingLocation,
                        onRetry = {
                            viewModel.refresh()
                            retry()
                        },
                        onHelpClick = {
                            uriHandler.openUri(URL_SECURITY_LEVEL_DOCUMENTATION)
                        },
                        onOpenSettings = {
                            startActivity(SettingsActivity.newInstance(requireContext()))
                        },
                        onChangeSecurityLevel = {
                            showConnectionSecurityLevelFragment(serverId)
                        },
                        onOpenLocationSettings = ::openLocationSettings,
                        onConfigureHomeNetwork = {
                            startActivity(
                                SettingsActivity.newInstance(
                                    context = requireContext(),
                                    screen = SettingsActivity.Deeplink.HomeNetwork(serverId),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun openLocationSettings() {
        if (DisabledLocationHandler.isLocationEnabled(requireContext())) {
            retry()
            return
        }
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        if (intent.resolveActivity(requireContext().packageManager) == null) {
            intent.action = Settings.ACTION_SETTINGS
        }
        startActivity(intent)
    }

    private fun retry() {
        // Only set the result - the Activity will handle closing the fragment
        // if conditions allow (avoids blink when still insecure)
        setFragmentResult(RESULT_KEY, Bundle())
    }

    private fun showConnectionSecurityLevelFragment(serverId: Int) {
        val fragment = ConnectionSecurityLevelFragment.newInstance(
            serverId = serverId,
            handleAllInsets = true,
        )
        parentFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
}
