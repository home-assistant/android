package io.homeassistant.companion.android.settings.shortcuts

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
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
import com.google.android.material.composethemeadapter.MdcTheme
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.shortcuts.views.ManageShortcutsView
import io.homeassistant.companion.android.util.icondialog.IconDialog
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.N_MR1)
@AndroidEntryPoint
class ManageShortcutsSettingsFragment : Fragment() {

    companion object {
        const val MAX_SHORTCUTS = 5
        const val SHORTCUT_PREFIX = "shortcut"
        private const val TAG = "ManageShortcutFrag"
    }

    val viewModel: ManageShortcutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-shortcuts"))
        }
    }
    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    var showingTag by remember { mutableStateOf<String?>(null) }
                    showingTag?.let { tag ->
                        IconDialog(
                            typeface = CommunityMaterial,
                            onSelect = {
                                onIconDialogIconsSelected(tag, it)
                                showingTag = null
                            },
                            onDismissRequest = { showingTag = null }
                        )
                    }

                    ManageShortcutsView(viewModel = viewModel, showIconDialog = { showingTag = it })
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onResume() {
        super.onResume()

        viewModel.updatePinnedShortcuts()
        activity?.title = getString(commonR.string.shortcuts)
    }

    private fun onIconDialogIconsSelected(tag: String, selectedIcon: IIcon) {
        Log.d(TAG, "Selected icon: $selectedIcon")

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
