package io.homeassistant.companion.android.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModel

/**
 * Fragment wrapper for [io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionScreen] to enable usage in Fragment-based navigation.
 *
 * The fragment bridges Fragment-based navigation with Compose Navigation's type-safe routing by
 * manually configuring the [androidx.lifecycle.SavedStateHandle] to match the expected
 * [io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute].
 *
 * ## Arguments
 * Pass `serverId` (Int) in the Fragment arguments Bundle to specify which server to configure.
 *
 * ## Migration Note
 * This fragment is temporary and should be removed once the app fully migrates to Compose Navigation.
 */
@AndroidEntryPoint
class ConnectionSecurityLevelFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "connection_security_level_result"
        const val EXTRA_SERVER = "server_id"
    }

    private val viewModel: LocationForSecureConnectionViewModel by createViewModelLazy(
        viewModelClass = LocationForSecureConnectionViewModel::class,
        storeProducer = { viewModelStore },
        extrasProducer = {
            // Extract serverId from Fragment arguments and inject into SavedStateHandle
            // to satisfy LocationForSecureConnectionRoute requirements
            val serverId = arguments?.getInt(EXTRA_SERVER, -1) ?: -1
            MutableCreationExtras(defaultViewModelCreationExtras).apply {
                // Key need to match the name of the attribute in the route
                set(DEFAULT_ARGS_KEY, bundleOf("serverId" to serverId))
            }
        },
    )

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val snackbarHostState = remember { SnackbarHostState() }
                val uriHandler = LocalUriHandler.current

                // Remaining insets to apply in settings activity
                val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

                HATheme {
                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.windowInsetsPadding(insets),
                            )
                        },
                        contentWindowInsets = insets,
                    ) {
                        LocationForSecureConnectionScreen(
                            isStandaloneScreen = true,
                            viewModel = viewModel,
                            onGoToNextScreen = {
                                setFragmentResult(RESULT_KEY, Bundle())
                                parentFragmentManager.popBackStack()
                            },
                            onBackClick = {
                                parentFragmentManager.popBackStack()
                            },
                            onHelpClick = {
                                uriHandler.openUri(
                                    "https://companion.home-assistant.io/docs/getting_started/connection-security-level",
                                )
                            },
                            onShowSnackbar = { message, action ->
                                snackbarHostState.showSnackbar(
                                    message,
                                    action,
                                    duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed
                            },
                            modifier = Modifier
                                // Consume status insets since the settings activity is already applying it
                                .consumeWindowInsets(WindowInsets.safeDrawing)
                                .padding(it),
                        )
                    }
                }
            }
        }
    }
}
