package io.homeassistant.companion.android.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.URL_SECURITY_LEVEL_DOCUMENTATION

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
        private const val EXTRA_SERVER = "server_id"
        private const val EXTRA_HANDLE_ALL_INSETS = "handle_all_insets"
        private const val EXTRA_USE_CLOSE_BUTTON = "use_close_button"

        /**
         * Creates a new instance of the fragment.
         *
         * @param serverId The server ID to configure security level for
         * @param handleAllInsets Whether to handle all window insets or only bottom if false
         * @param useCloseButton If true, shows an X close button; if false, shows a back arrow.
         */
        fun newInstance(
            serverId: Int,
            handleAllInsets: Boolean = false,
            useCloseButton: Boolean = false,
        ): ConnectionSecurityLevelFragment {
            return ConnectionSecurityLevelFragment().apply {
                arguments = bundleOf(
                    EXTRA_SERVER to serverId,
                    EXTRA_HANDLE_ALL_INSETS to handleAllInsets,
                    EXTRA_USE_CLOSE_BUTTON to useCloseButton,
                )
            }
        }
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

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onDismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val snackbarHostState = remember { SnackbarHostState() }
                val uriHandler = LocalUriHandler.current

                // Remaining insets to apply in settings activity
                val insets =
                    if (arguments?.getBoolean(EXTRA_HANDLE_ALL_INSETS) ==
                        true
                    ) {
                        WindowInsets.safeDrawing
                    } else {
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom,
                        )
                    }

                val useCloseButton = arguments?.getBoolean(EXTRA_USE_CLOSE_BUTTON) == true
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
                            onGoToNextScreen = { onDismiss() },
                            onBackClick = if (useCloseButton) null else ::onDismiss,
                            onCloseClick = if (useCloseButton) ::onDismiss else null,
                            onHelpClick = {
                                uriHandler.openUri(URL_SECURITY_LEVEL_DOCUMENTATION)
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

    private fun onDismiss() {
        setFragmentResult(RESULT_KEY, Bundle())
        parentFragmentManager.popBackStack()
    }
}
