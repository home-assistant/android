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
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.controls.views.ManageControlsView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@AndroidEntryPoint
class ManageControlsSettingsFragment : Fragment() {

    val viewModel: ManageControlsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ManageControlsView(
                        panelEnabled = viewModel.panelEnabled,
                        authSetting = viewModel.authRequired,
                        authRequiredList = viewModel.authRequiredList,
                        entitiesLoaded = viewModel.entitiesLoaded,
                        entitiesList = viewModel.entitiesList,
                        panelSetting = viewModel.panelSetting,
                        serversList = viewModel.defaultServers,
                        defaultServer = viewModel.defaultServerId,
                        structureEnabled = viewModel.structureEnabled,
                        onSetPanelEnabled = viewModel::enablePanelForControls,
                        onSelectAll = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.NONE) },
                        onSelectNone = { viewModel.setAuthSetting(ControlsAuthRequiredSetting.ALL) },
                        onSelectEntity = { entityId, serverId -> viewModel.toggleAuthForEntity(entityId, serverId) },
                        onSetPanelSetting = { path, serverId -> viewModel.setPanelConfig(path, serverId) },
                        onSetStructureEnabled = { enabled -> viewModel.setStructureEnable(enabled) },
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
