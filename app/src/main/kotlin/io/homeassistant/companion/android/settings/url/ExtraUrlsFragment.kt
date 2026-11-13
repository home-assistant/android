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
import io.homeassistant.companion.android.settings.url.views.ExtraUrlsView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class ExtraUrlsFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    private val viewModel by viewModels<ExtraUrlsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ExtraUrlsView(
                        urls = viewModel.urls,
                        error = viewModel.error,
                        onAdd = viewModel::addUrl,
                        onRemove = { viewModel.removeUrl(it) },
                        onMove = { index, offset -> viewModel.moveUrl(index, offset) },
                        onInputChanged = viewModel::clearError,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.pref_connection_extra_urls)
    }
}
