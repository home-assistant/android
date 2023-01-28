package io.homeassistant.companion.android.settings.controls

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.settings.controls.views.ManageControlsView
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@AndroidEntryPoint
class ManageControlsSettingsFragment : Fragment() {

    val viewModel: ManageControlsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-device-controls"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    ManageControlsView(
                        authSetting = viewModel.authRequired,
                        authRequiredList = viewModel.authRequiredList,
                        entitiesLoaded = viewModel.entitiesLoaded,
                        entitiesList = viewModel.entitiesList,
                        onSelectAll = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.NONE) },
                        onSelectNone = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.ALL) },
                        onSelectEntity = { viewModel.toggleAuthForEntity(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.controls_setting_title)
    }
}
