package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.settings.sensor.views.SensorDetailView
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SensorDetailFragment : Fragment() {

    companion object {
        fun newInstance(sensorId: String): SensorDetailFragment {
            return SensorDetailFragment().apply {
                arguments = Bundle().apply { putString("id", sensorId) }
            }
        }
    }

    val viewModel: SensorDetailViewModel by viewModels()

    private var requestForServer: Int? = null
    private val activityResultRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onActivityResult(requestForServer)
    }
    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionsResult(it, requestForServer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.serversShowExpand.collect { updateSensorToolbarMenu() }
                }
                launch {
                    viewModel.serversDoExpand.collect { updateSensorToolbarMenu() }
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.setGroupVisible(R.id.senor_detail_toolbar_group, true)
        menu.removeItem(R.id.action_filter)
        menu.removeItem(R.id.action_search)

        menu.setGroupVisible(R.id.sensor_detail_server_group, true)
        menu.findItem(R.id.action_sensor_expand)?.let {
            it.setOnMenuItemClickListener {
                viewModel.setServersExpanded(true)
                true
            }
        }
        menu.findItem(R.id.action_sensor_collapse)?.let {
            it.setOnMenuItemClickListener {
                viewModel.setServersExpanded(false)
                true
            }
        }
        updateSensorToolbarMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            val docsLink = viewModel.basicSensor?.docsLink ?: viewModel.sensorManager?.docsLink()
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse(docsLink))
            it.isVisible = docsLink != null // should always be true
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
                    SensorDetailView(
                        viewModel = viewModel,
                        onSetEnabled = { enable, serverId -> viewModel.setEnabled(enable, serverId) },
                        onToggleSettingSubmitted = { setting -> viewModel.setSetting(setting) },
                        onDialogSettingClicked = { setting -> viewModel.onSettingWithDialogPressed(setting) },
                        onDialogSettingSubmitted = { state -> viewModel.submitSettingWithDialog(state) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.permissionRequests.observe(viewLifecycleOwner) {
            if (it == null || it.permissions.isNullOrEmpty()) return@observe
            requestForServer = it.serverId
            when {
                it.permissions.any { perm -> perm == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                it.permissions.any { perm -> perm == Manifest.permission.PACKAGE_USAGE_STATS } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                    if (it.permissions.size == 1 && it.permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                        permissionsRequest.launch(it.permissions)
                    } else {
                        permissionsRequest.launch(it.permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray())
                    }
                else -> permissionsRequest.launch(it.permissions)
            }
        }
        viewModel.locationPermissionRequests.observe(viewLifecycleOwner) {
            it?.let {
                if (it.block) {
                    DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), it.sensors)
                } else {
                    LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(
                        requireContext(),
                        it.permissions!!,
                        continueYesCallback = {
                            requestForServer = it.serverId
                            permissionsRequest.launch(
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    it.permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray()
                                } else {
                                    it.permissions
                                }
                            )
                        }
                    )
                }
                viewModel.locationPermissionRequests.value = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = null
    }

    private fun updateSensorToolbarMenu(menu: Menu? = null) {
        val group = if (menu != null) {
            menu
        } else {
            if (view == null || activity == null) return
            val toolbar = activity?.findViewById<Toolbar>(R.id.toolbar) ?: return
            toolbar.menu
        }
        group.findItem(R.id.action_sensor_expand)?.let {
            it.isVisible = viewModel.serversShowExpand.value && !viewModel.serversDoExpand.value
        }
        group.findItem(R.id.action_sensor_collapse)?.let {
            it.isVisible = viewModel.serversShowExpand.value && viewModel.serversDoExpand.value
        }
    }
}
