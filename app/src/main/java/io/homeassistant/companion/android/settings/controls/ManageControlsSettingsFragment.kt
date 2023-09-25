package io.homeassistant.companion.android.settings.controls

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.controls.views.ManageControlsView
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@AndroidEntryPoint
class ManageControlsSettingsFragment : Fragment() {

    @Inject
    lateinit var serverManager: ServerManager

    val viewModel: ManageControlsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    ManageControlsView(
                        panelEnabled = viewModel.panelEnabled,
                        authSetting = viewModel.authRequired,
                        authRequiredList = viewModel.authRequiredList,
                        entitiesLoaded = viewModel.entitiesLoaded,
                        entitiesList = viewModel.entitiesList,
                        panelSetting = viewModel.panelSetting,
                        serversList = serverManager.defaultServers,
                        defaultServer = serverManager.getServer()?.id ?: 0,
                        onSetPanelEnabled = viewModel::enablePanelForControls,
                        onSelectAll = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.NONE) },
                        onSelectNone = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.ALL) },
                        onSelectEntity = { entityId, serverId -> viewModel.toggleAuthForEntity(entityId, serverId) },
                        onSetPanelSetting = { path, serverId -> viewModel.setPanelConfig(path, serverId) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/android-device-controls")
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.controls_setting_title)
    }
}
