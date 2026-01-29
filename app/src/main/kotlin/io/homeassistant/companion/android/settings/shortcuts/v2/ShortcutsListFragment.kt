package io.homeassistant.companion.android.settings.shortcuts.v2

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.shortcuts.v2.navigation.ShortcutsNavHost
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.safeBottomWindowInsets

@RequiresApi(Build.VERSION_CODES.N_MR1)
@AndroidEntryPoint
class ShortcutsListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val snackbarHostState = remember { SnackbarHostState() }

                HomeAssistantAppTheme {
                    HATheme {
                        Scaffold(
                            snackbarHost = {
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier.Companion.windowInsetsPadding(
                                        safeBottomWindowInsets(applyHorizontal = false),
                                    ),
                                )
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        ) { contentPadding ->
                            Box(modifier = Modifier.Companion.padding(contentPadding)) {
                                ShortcutsNavHost(
                                    onToolbarTitleChanged = { title ->
                                        activity?.title = title
                                    },
                                    onShowSnackbar = { message ->
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            duration = SnackbarDuration.Short,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/android-shortcuts")
    }
}
