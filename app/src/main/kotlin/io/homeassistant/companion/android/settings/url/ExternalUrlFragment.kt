package io.homeassistant.companion.android.settings.url

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.url.views.ExternalUrlView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class ExternalUrlFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    val viewModel by viewModels<ExternalUrlViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ExternalUrlView(
                        canUseCloud = viewModel.canUseCloud,
                        useCloud = viewModel.useCloud,
                        externalUrl = viewModel.externalUrl,
                        onUseCloudToggle = { viewModel.toggleCloud(it) },
                        onExternalUrlSaved = { viewModel.updateExternalUrl(it) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.input_url)
    }
}
