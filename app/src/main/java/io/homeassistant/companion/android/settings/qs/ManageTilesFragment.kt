package io.homeassistant.companion.android.settings.qs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.pack.IconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.qs.views.ManageTilesView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageTilesFragment : Fragment(), IconDialog.Callback {

    companion object {
        private const val TAG = "TileFragment"
        val validDomains = listOf(
            "button", "cover", "fan", "humidifier", "input_boolean", "input_button", "light",
            "lock", "media_player", "remote", "siren", "scene", "script", "switch"
        )
    }

    val viewModel: ManageTilesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            val settings = IconDialogSettings {
                searchVisibility = IconDialog.SearchVisibility.ALWAYS
            }
            val iconDialog = IconDialog.newInstance(settings)

            setContent {
                MdcTheme {
                    ManageTilesView(
                        viewModel = viewModel,
                        onShowIconDialog = { tag ->
                            iconDialog.show(childFragmentManager, tag)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/android-quick-settings".toUri())
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.tiles)
    }

    override val iconDialogIconPack: IconPack
        get() = viewModel.iconPack

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<com.maltaisn.icondialog.data.Icon>) {
        Log.d(TAG, "Selected icon: ${icons.firstOrNull()}")
        val selectedIcon = icons.firstOrNull()
        if (selectedIcon != null) {
            viewModel.selectIcon(selectedIcon)
        }
    }
}
