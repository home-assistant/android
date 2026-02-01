package io.homeassistant.companion.android.settings.shortcuts

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
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
import io.homeassistant.companion.android.settings.shortcuts.views.ManageShortcutsView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.icondialog.IconDialog
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N_MR1)
@AndroidEntryPoint
class ManageShortcutsSettingsFragment : Fragment() {

    companion object {
        const val MAX_SHORTCUTS = 5
        const val SHORTCUT_PREFIX = "shortcut"
    }

    val viewModel: ManageShortcutsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    var showingTag by remember { mutableStateOf<String?>(null) }
                    showingTag?.let { tag ->
                        IconDialog(
                            onSelect = {
                                onIconDialogIconsSelected(tag, it)
                                showingTag = null
                            },
                            onDismissRequest = { showingTag = null },
                        )
                    }

                    ManageShortcutsView(viewModel = viewModel, showIconDialog = { showingTag = it })
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/android-shortcuts")
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onResume() {
        super.onResume()

        viewModel.updatePinnedShortcuts()
        activity?.title = getString(commonR.string.shortcuts)
    }

    private fun onIconDialogIconsSelected(tag: String, selectedIcon: IIcon) {
        Timber.d("Selected icon: $selectedIcon")

        val index = when (tag) {
            "shortcut_1" -> 0
            "shortcut_2" -> 1
            "shortcut_3" -> 2
            "shortcut_4" -> 3
            "shortcut_5" -> 4
            else -> 5
        }
        viewModel.shortcuts[index].selectedIcon.value = selectedIcon
    }
}
