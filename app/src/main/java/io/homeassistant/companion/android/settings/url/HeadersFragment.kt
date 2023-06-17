package io.homeassistant.companion.android.settings.url

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.url.views.HeadersView

@AndroidEntryPoint
class HeadersFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    val viewModel by viewModels<HeadersViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (viewModel.headers.size == 0) {
            viewModel.headers[""] = ""
        }
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    HeadersView( headersViewModel = viewModel )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.pref_headers)
    }

}