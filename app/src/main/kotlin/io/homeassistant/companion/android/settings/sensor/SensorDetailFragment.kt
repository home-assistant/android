package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.sensors.HealthConnectSensorManager
import io.homeassistant.companion.android.settings.sensor.views.SensorDetailView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
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
    private var healthPermissionsRequest: ActivityResultLauncher<Set<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.serversShowExpand.collect { activity?.invalidateMenu() }
                }
                launch {
                    viewModel.serversDoExpand.collect { activity?.invalidateMenu() }
                }
            }
        }

        HealthConnectSensorManager.getPermissionResultContract()?.let { contract ->
            healthPermissionsRequest = registerForActivityResult(contract) {
                viewModel.onPermissionsResult(it.associateWith { true }, requestForServer)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    SensorDetailView(
                        viewModel = viewModel,
                        onSetEnabled = { enable, serverId -> viewModel.setEnabled(enable, serverId) },
                        onToggleSettingSubmitted = { setting -> viewModel.setSetting(setting) },
                        onDialogSettingClicked = { setting -> viewModel.onSettingWithDialogPressed(setting) },
                        onDialogSettingSubmitted = { state -> viewModel.submitSettingWithDialog(state) },
                    )
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_fragment_sensordetail, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    menu.findItem(R.id.action_sensor_expand)?.let {
                        it.isVisible = viewModel.serversShowExpand.value && !viewModel.serversDoExpand.value
                    }
                    menu.findItem(R.id.action_sensor_collapse)?.let {
                        it.isVisible = viewModel.serversShowExpand.value && viewModel.serversDoExpand.value
                    }
                    menu.findItem(R.id.get_help)?.let {
                        val docsLink = viewModel.basicSensor?.docsLink ?: viewModel.sensorManager?.docsLink()
                        it.isVisible = docsLink != null
                        if (docsLink != null) {
                            it.intent = Intent(Intent.ACTION_VIEW, docsLink.toUri())
                        }
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                    R.id.action_sensor_expand, R.id.action_sensor_collapse -> {
                        viewModel.setServersExpanded(menuItem.itemId == R.id.action_sensor_expand)
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        viewModel.permissionRequests.observe(viewLifecycleOwner) {
            if (it == null || it.permissions.isNullOrEmpty()) return@observe
            requestForServer = it.serverId
            when {
                it.permissions.any { perm -> perm == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                it.permissions.any { perm -> perm == Manifest.permission.PACKAGE_USAGE_STATS } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                it.permissions.any { perm -> perm.startsWith("android.permission.health") } -> {
                    val healthConnectPermissions = it.permissions.filter { perm ->
                        perm.startsWith("android.permission.health")
                    }
                    healthPermissionsRequest?.launch(healthConnectPermissions.toSet())
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    if (it.permissions.size == 1 &&
                        it.permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) {
                        permissionsRequest.launch(it.permissions)
                    } else {
                        permissionsRequest.launch(
                            it.permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray(),
                        )
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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    it.permissions.toSet().minus(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                    ).toTypedArray()
                                } else {
                                    it.permissions
                                },
                            )
                        },
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
}
