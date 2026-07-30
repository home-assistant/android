package io.homeassistant.companion.android.changelog

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.changelog.ui.ChangelogContent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.safeBottomWindowInsets

/**
 * Hosts the changelog within the settings, whose activity already provides the toolbar with the
 * title and back navigation.
 */
@AndroidEntryPoint
class ChangelogFragment : Fragment() {

    private val viewModel: ChangelogViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    ChangelogContent(
                        uiState = uiState,
                        onGotItClick = { parentFragmentManager.popBackStack() },
                        onActionClick = ::onActionClick,
                        modifier = Modifier
                            .background(LocalHAColorScheme.current.colorSurfaceDefault)
                            .windowInsetsPadding(safeBottomWindowInsets(applyHorizontal = false)),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.changelog_screen_title)
    }

    private fun onActionClick(action: ChangelogAction) {
        when (action) {
            is ChangelogAction.OpenUrl -> startActivity(Intent(Intent.ACTION_VIEW, action.url.toUri()))
            is ChangelogAction.OpenSettings ->
                startActivity(SettingsActivity.newInstance(requireContext(), action.deeplink))
            is ChangelogAction.OpenWidgetConfig ->
                startActivity(action.widgetType.toConfigureIntent(requireContext()))
        }
    }
}
