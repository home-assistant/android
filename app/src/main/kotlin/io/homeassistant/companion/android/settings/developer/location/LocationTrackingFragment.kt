package io.homeassistant.companion.android.settings.developer.location

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.developer.location.views.LocationTrackingView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject

private const val FAQ_LINK =
    "https://companion.home-assistant.io/docs/troubleshooting/faqs#device-tracker-is-not-updating-in-android-app"

@AndroidEntryPoint
class LocationTrackingFragment : Fragment() {

    @Inject
    lateinit var serverManager: ServerManager

    val viewModel: LocationTrackingViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    LocationTrackingView(
                        useHistory = viewModel.historyEnabled,
                        onSetHistory = viewModel::enableHistory,
                        history = viewModel.historyPagerFlow,
                        serversList = serverManager.defaultServers,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_fragment_locationtracking, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    menu.findItem(R.id.get_help).apply {
                        intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                FAQ_LINK.toUri(),
                            )
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                    R.id.history_all, R.id.history_sent, R.id.history_skipped, R.id.history_failed -> {
                        viewModel.setHistoryFilter(menuItem.itemId)
                        menuItem.isChecked = true
                        true
                    }

                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.location_tracking)
    }
}
