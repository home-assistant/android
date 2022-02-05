package io.homeassistant.companion.android.settings.qs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
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
import io.homeassistant.companion.android.settings.qs.views.ManageTilesView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageTilesFragment constructor(
    val integrationRepository: IntegrationRepository
) : Fragment(), IconDialog.Callback {

    companion object {
        private const val TAG = "TileFragment"
        val validDomains = listOf(
            "button", "cover", "fan", "humidifier", "input_boolean", "input_button", "light",
            "media_player", "remote", "siren", "scene", "script", "switch"
        )
    }

    val viewModel: ManageTilesViewModel by viewModels()
    private lateinit var iconPack: IconPack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-quick-settings"))
        }
    }

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
                    ManageTilesView(viewModel = viewModel, iconDialog = iconDialog, childFragment = childFragmentManager)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val loader = IconPackLoader(requireContext())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)

        activity?.title = getString(commonR.string.tiles)
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
                viewModel.selectedIcon.value = selectedIcon.id
                viewModel.drawableIcon.value = icon
            }
        }
    }
}
