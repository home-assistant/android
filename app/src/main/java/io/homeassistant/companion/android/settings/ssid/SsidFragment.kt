package io.homeassistant.companion.android.settings.ssid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.ssid.views.SsidView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SsidFragment : Fragment() {

    val viewModel: SsidViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/troubleshooting/networking#setting-up-the-app"))
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
                    SsidView(
                        wifiSsids = viewModel.wifiSsids,
                        prioritizeInternal = viewModel.prioritizeInternal,
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

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.pref_connection_wifi)
    }
}
