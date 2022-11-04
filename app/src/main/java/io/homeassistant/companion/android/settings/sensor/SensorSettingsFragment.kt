package io.homeassistant.companion.android.settings.sensor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.settings.sensor.views.SensorListView
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SensorSettingsFragment : Fragment() {

    val viewModel: SensorSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.setGroupVisible(R.id.senor_detail_toolbar_group, true)

        val searchViewItem = menu.findItem(R.id.action_search)
        val searchView: SearchView = searchViewItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(query: String?): Boolean {
                viewModel.setSensorsSearchQuery(query)
                return false
            }
        })
        if (!viewModel.searchQuery.isNullOrBlank() && !searchViewItem.isActionViewExpanded) {
            viewModel.setSensorsSearchQuery(null)
        }

        if (viewModel.showOnlyEnabledSensors.value) {
            val checkable = menu.findItem(R.id.action_show_only_enabled_sensors)
            checkable?.isChecked = true
        }

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/core/sensors#android-sensors"))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_only_enabled_sensors -> {
                item.isChecked = !item.isChecked
                viewModel.setShowOnlyEnabledSensors(item.isChecked)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    SensorListView(
                        viewModel = viewModel,
                        managers = SensorReceiver.MANAGERS.sortedBy { getString(it.name) },
                        onSensorClicked = { sensor ->
                            parentFragmentManager
                                .beginTransaction()
                                .replace(
                                    R.id.content,
                                    SensorDetailFragment.newInstance(
                                        sensor
                                    )
                                )
                                .addToBackStack("Sensor Detail")
                                .commit()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.sensors)
    }
}
