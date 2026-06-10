package io.homeassistant.companion.android.settings.server

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject

@AndroidEntryPoint
class ServerChooserFragment : DialogFragment() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var serverChooserItems: ServerChooserItemsManager

    companion object {
        const val TAG = "ServerChooser"

        const val RESULT_KEY = "ServerChooserResult"
        const val RESULT_SERVER = "server"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val items by produceState(initialValue = emptyList()) {
                    value = serverChooserItems(serverManager.servers())
                }
                HATheme {
                    ServerChooser(
                        items = items,
                        onServerSelected = { serverId ->
                            setFragmentResult(RESULT_KEY, bundleOf(RESULT_SERVER to serverId))
                            dismiss()
                        },
                        onDismissRequest = ::dismiss,
                    )
                }
            }
        }
    }
}
