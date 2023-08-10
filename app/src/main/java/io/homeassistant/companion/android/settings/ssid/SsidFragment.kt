package io.homeassistant.companion.android.settings.ssid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.ssid.views.SsidView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SsidFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    val viewModel: SsidViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    SsidView(
                        wifiSsids = viewModel.wifiSsids,
                        prioritizeInternal = viewModel.prioritizeInternal,
                        usingWifi = viewModel.usingWifi,
                        activeSsid = viewModel.activeSsid,
                        activeBssid = viewModel.activeBssid,
                        onAddWifiSsid = viewModel::addHomeWifiSsid,
                        onRemoveWifiSsid = viewModel::removeHomeWifiSsid,
                        onSetPrioritize = viewModel::setPrioritize
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/troubleshooting/networking#setting-up-the-app")
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.pref_connection_wifi)
    }
}
