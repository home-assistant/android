package io.homeassistant.companion.android.settings.qs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.mikepenz.iconics.typeface.IIcon
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.qs.views.ManageTilesView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.icondialog.IconDialog
import timber.log.Timber

@AndroidEntryPoint
class ManageTilesFragment : Fragment() {
    val viewModel: ManageTilesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    var showingDialog by remember { mutableStateOf(false) }

                    if (showingDialog) {
                        IconDialog(
                            onSelect = {
                                onIconDialogIconsSelected(it)
                                showingDialog = false
                            },
                            onDismissRequest = { showingDialog = false },
                        )
                    }

                    ManageTilesView(
                        viewModel = viewModel,
                        onShowIconDialog = {
                            showingDialog = true
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/android-quick-settings")
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.tiles)
    }

    private fun onIconDialogIconsSelected(selectedIcon: IIcon) {
        Timber.d("Selected icon: ${selectedIcon.name}")
        viewModel.selectIcon(selectedIcon)
    }
}
