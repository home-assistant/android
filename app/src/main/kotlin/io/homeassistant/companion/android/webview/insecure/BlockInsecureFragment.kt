package io.homeassistant.companion.android.webview.insecure

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.settings.ConnectionSecurityLevelFragment
import io.homeassistant.companion.android.settings.SettingsActivity

/**
 * Fragment explaining why the current connection is blocked.
 */
@AndroidEntryPoint
class BlockInsecureFragment private constructor() : Fragment() {

    companion object {
        const val RESULT_KEY = "block_insecure_result"
        private const val EXTRA_SERVER = "server_id"
        private const val EXTRA_MISSING_HOME_SETUP = "missing_home_setup"
        private const val EXTRA_MISSING_LOCATION = "missing_location"

        fun newInstance(serverId: Int, missingHomeSetup: Boolean, missingLocation: Boolean): BlockInsecureFragment {
            return BlockInsecureFragment().apply {
                arguments = Bundle().apply {
                    putInt(EXTRA_SERVER, serverId)
                    putBoolean(EXTRA_MISSING_HOME_SETUP, missingHomeSetup)
                    putBoolean(EXTRA_MISSING_LOCATION, missingLocation)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val serverId = arguments?.getInt(EXTRA_SERVER, -1) ?: -1
        val missingHomeSetup = arguments?.getBoolean(EXTRA_MISSING_HOME_SETUP, false) ?: false
        val missingLocation = arguments?.getBoolean(EXTRA_MISSING_LOCATION, false) ?: false

        return ComposeView(requireContext()).apply {
            setContent {
                val uriHandler = LocalUriHandler.current
                HATheme {
                    BlockInsecureScreen(
                        missingHomeSetup = missingHomeSetup,
                        missingLocation = missingLocation,
                        onRetry = ::retryAndClose,
                        onHelpClick = {
                            uriHandler.openUri(
                                "https://companion.home-assistant.io/docs/getting_started/connection-security-level/",
                            )
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
            retryAndClose()
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

    private fun retryAndClose() {
        setFragmentResult(RESULT_KEY, Bundle())
        parentFragmentManager.popBackStack()
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
