package io.homeassistant.companion.android.settings.developer.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.developer.location.views.LocationTrackingView

@AndroidEntryPoint
class LocationTrackingFragment : Fragment() {

    val viewModel: LocationTrackingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    LocationTrackingView(
                        useHistory = viewModel.historyEnabled,
                        onSetHistory = viewModel::enableHistory,
                        history = viewModel.historyPagerFlow
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/troubleshooting/faqs#device-tracker-is-not-updating-in-android-app")
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.location_tracking)
    }
}
