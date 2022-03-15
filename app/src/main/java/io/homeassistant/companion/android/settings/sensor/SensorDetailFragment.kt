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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.settings.sensor.views.SensorDetailView

@AndroidEntryPoint
class SensorDetailFragment(val sensorId: String) : Fragment() {

    companion object {
        fun newInstance(sensorId: String): SensorDetailFragment {
            return SensorDetailFragment(sensorId).apply {
                arguments = Bundle().apply { putString("id", sensorId) }
            }
        }
    }

    val viewModel: SensorDetailViewModel by viewModels()

    private val activityResultRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onActivityResult()
    }
    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionsResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.setGroupVisible(R.id.senor_detail_toolbar_group, true)
        menu.removeItem(R.id.action_filter)
        menu.removeItem(R.id.action_search)

        menu.findItem(R.id.get_help)?.let {
            val docsLink = viewModel.basicSensor?.docsLink ?: viewModel.sensorManager?.docsLink()
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse(docsLink))
            it.isVisible = docsLink != null // should always be true
        }
    }

    @ExperimentalComposeUiApi
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
                        onSetEnabled = { enable -> viewModel.setEnabled(enable) },
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

        viewModel.permissionRequests.observe(viewLifecycleOwner) { permissions ->
            if (permissions.isEmpty()) return@observe
            when {
                permissions.any { perm -> perm == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                permissions.any { perm -> perm == Manifest.permission.PACKAGE_USAGE_STATS } ->
                    activityResultRequest.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                    if (permissions.size == 1 && permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                        permissionsRequest.launch(permissions)
                    } else {
                        permissionsRequest.launch(permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray())
                    }
                else -> permissionsRequest.launch(permissions)
            }
        }
        viewModel.locationPermissionRequests.observe(viewLifecycleOwner) {
            it?.let {
                if (it.block) {
                    DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), it.sensors)
                } else {
                    LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(
                        requireContext(), it.permissions!!,
                        continueYesCallback = {
                            permissionsRequest.launch(
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    it.permissions.toSet().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray()
                                } else it.permissions
                            )
                        }
                    )
                }
                viewModel.locationPermissionRequests.value = null
            }
        }
    }
}
