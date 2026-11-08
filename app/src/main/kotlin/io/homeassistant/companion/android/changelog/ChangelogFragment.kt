package io.homeassistant.companion.android.changelog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.changelog.ui.ChangelogContent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.launch

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
                    val coroutineScope = rememberCoroutineScope()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val bottomInsets = safeBottomWindowInsets(applyHorizontal = false)

                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.windowInsetsPadding(bottomInsets),
                            )
                        },
                        // The content applies the insets it needs itself, the settings activity handles the rest.
                        contentWindowInsets = WindowInsets(0),
                    ) { contentPadding ->
                        ChangelogContent(
                            uiState = uiState,
                            onGotItClick = { parentFragmentManager.popBackStack() },
                            onActionClick = { action ->
                                coroutineScope.launch {
                                    action.perform(requireContext()) { message, snackbarAction ->
                                        snackbarHostState.showSnackbar(
                                            message,
                                            snackbarAction,
                                            duration = SnackbarDuration.Short,
                                        ) == SnackbarResult.ActionPerformed
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(contentPadding)
                                .background(LocalHAColorScheme.current.colorSurfaceDefault)
                                .windowInsetsPadding(bottomInsets),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.changelog_screen_title)
    }
}
