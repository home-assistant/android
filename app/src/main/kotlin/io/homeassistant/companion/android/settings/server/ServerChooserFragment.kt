package io.homeassistant.companion.android.settings.server

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.setLayoutAndExpandedByDefault
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ServerChooserFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var serverChooserItemsUseCase: ServerChooserItemsUseCase

    private var hasResult = false

    companion object {
        const val TAG = "ServerChooser"

        const val RESULT_KEY = "ServerChooserResult"
        const val RESULT_SERVER = "server"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val items by produceState(initialValue = emptyList<ServerChooserItem>()) {
                    serverManager.serversFlow.collectLatest { servers ->
                        serverChooserItemsUseCase(servers).collect { value = it }
                    }
                }
                HATheme {
                    ServerChooserContent(
                        items = items,
                        onServerSelected = { serverId ->
                            hasResult = true
                            setFragmentResult(RESULT_KEY, Bundle().apply { putInt(RESULT_SERVER, serverId) })
                            dismiss()
                        },
                        modifier = Modifier.background(
                            color = LocalHAColorScheme.current.colorSurfaceDefault,
                            shape = RoundedCornerShape(topStart = HARadius.X3L, topEnd = HARadius.X3L),
                        ),
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setLayoutAndExpandedByDefault()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!hasResult) {
            setFragmentResult(RESULT_KEY, Bundle.EMPTY)
        }
    }
}
