package io.homeassistant.companion.android.settings.shortcuts

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.shortcuts.views.ManageShortcutsView
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageShortcutsSettingsFragment : Fragment(), IconDialog.Callback {

    companion object {
        const val MAX_SHORTCUTS = 5
        const val SHORTCUT_PREFIX = "shortcut"
        private const val TAG = "ManageShortcutFrag"
    }

    val viewModel: ManageShortcutsViewModel by viewModels()
    private lateinit var iconPack: IconPack

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

    @RequiresApi(Build.VERSION_CODES.N_MR1)
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
                    ManageShortcutsView(viewModel = viewModel, iconDialog = iconDialog, childFragment = childFragmentManager)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onResume() {
        super.onResume()

        val loader = IconPackLoader(requireContext())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)

        viewModel.updatePinnedShortcuts()
        activity?.title = getString(commonR.string.shortcuts)
    }

    override val iconDialogIconPack: IconPack
        get() = iconPack

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<com.maltaisn.icondialog.data.Icon>) {
        Log.d(TAG, "Selected icon: ${icons.firstOrNull()}")
        val selectedIcon = icons.firstOrNull()
        if (selectedIcon != null) {
            val iconDrawable = selectedIcon.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                icon.setColorFilter(resources.getColor(commonR.color.colorAccent), PorterDuff.Mode.SRC_IN)
                when (dialog.tag) {
                    "shortcut_1" -> {
                        viewModel.shortcuts[0].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[0].drawable.value = icon
                    }
                    "shortcut_2" -> {
                        viewModel.shortcuts[1].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[1].drawable.value = icon
                    }
                    "shortcut_3" -> {
                        viewModel.shortcuts[2].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[2].drawable.value = icon
                    }
                    "shortcut_4" -> {
                        viewModel.shortcuts[3].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[3].drawable.value = icon
                    }
                    "shortcut_5" -> {
                        viewModel.shortcuts[4].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[4].drawable.value = icon
                    }
                    else -> {
                        viewModel.shortcuts[5].selectedIcon.value = selectedIcon.id
                        viewModel.shortcuts[5].drawable.value = icon
                    }
                }
            }
        }
    }
}
