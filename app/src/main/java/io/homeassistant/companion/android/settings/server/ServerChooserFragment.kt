package io.homeassistant.companion.android.settings.server

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject

@AndroidEntryPoint
class ServerChooserFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var serverManager: ServerManager

    companion object {
        const val TAG = "ServerChooser"

        const val RESULT_KEY = "ServerChooserResult"
        const val RESULT_SERVER = "server"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    ServerChooserView(
                        servers = serverManager.defaultServers,
                        onServerSelected = { serverId ->
                            setFragmentResult(RESULT_KEY, bundleOf(RESULT_SERVER to serverId))
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnShowListener {
            val dialog = it as BottomSheetDialog
            val bottomSheet = dialog.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}
