package io.homeassistant.companion.android.settings.sensor

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
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
import io.homeassistant.companion.android.settings.sensor.views.SensorListView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class SensorSettingsFragment : Fragment() {

    val viewModel: SensorSettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    SensorListView(
                        viewModel = viewModel,
                        onSensorClicked = { sensor ->
                            parentFragmentManager
                                .beginTransaction()
                                .replace(
                                    R.id.content,
                                    SensorDetailFragment.newInstance(
                                        sensor,
                                    ),
                                )
                                .addToBackStack("Sensor Detail")
                                .commit()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_fragment_sensor, menu)

                    val searchViewItem = menu.findItem(R.id.action_search)
                    val searchView = searchViewItem.actionView as SearchView
                    searchView.apply {
                        queryHint = getString(commonR.string.search_sensors)
                        maxWidth = Integer.MAX_VALUE
                    }
                }

                override fun onPrepareMenu(menu: Menu) {
                    val searchViewItem = menu.findItem(R.id.action_search)
                    val searchView = searchViewItem.actionView as SearchView
                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?): Boolean {
                                searchView.clearFocus()
                                return false
                            }

                            override fun onQueryTextChange(query: String?): Boolean {
                                viewModel.setSensorsSearchQuery(query)
                                return false
                            }
                        },
                    )
                    if (!viewModel.searchQuery.isNullOrBlank() && !searchViewItem.isActionViewExpanded) {
                        viewModel.setSensorsSearchQuery(null)
                    }

                    menu.findItem(viewModel.sensorFilter.menuItemId)?.isChecked = true

                    menu.findItem(R.id.get_help)?.let {
                        it.isVisible = true
                        it.intent =
                            Intent(
                                ACTION_VIEW,
                                "https://companion.home-assistant.io/docs/core/sensors#android-sensors".toUri(),
                            )
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                    R.id.action_show_sensors_all,
                    R.id.action_show_sensors_enabled,
                    R.id.action_show_sensors_disabled,
                    -> {
                        menuItem.isChecked = !menuItem.isChecked
                        viewModel.setSensorFilterChoice(menuItem.itemId)
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
        activity?.title = getString(commonR.string.sensors)
    }
}
