package io.homeassistant.companion.android.settings.network

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.settings.addHelpMenuProvider

@AndroidEntryPoint
class RequestTimeoutFragment : Fragment() {
    private val viewModel: RequestTimeoutViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { HATheme { RequestTimeoutScreen(viewModel) } }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider(
            "https://companion.home-assistant.io/docs/troubleshooting/faqs/#android-network-request-timeout",
        )
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.network_request_timeout)
    }
}
