package io.homeassistant.companion.android.settings.ssid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.ssid.views.SsidView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class SsidFragment : Fragment() {

    companion object {
        const val EXTRA_SERVER = "server"
    }

    val viewModel: SsidViewModel by viewModels()

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsResult(it)
    }

    private var canReadWifi by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    SsidView(
                        wifiSsids = viewModel.wifiSsids,
                        canReadWifi = canReadWifi,
                        ethernet = viewModel.ethernet,
                        vpn = viewModel.vpn,
                        prioritizeInternal = viewModel.prioritizeInternal,
                        usingWifi = viewModel.usingWifi,
                        activeSsid = viewModel.activeSsid,
                        activeBssid = viewModel.activeBssid,
                        onAddWifiSsid = viewModel::addHomeWifiSsid,
                        onRemoveWifiSsid = viewModel::removeHomeWifiSsid,
                        onRequestPermission = { onRequestLocationPermission() },
                        onSetEthernet = viewModel::setInternalWithEthernet,
                        onSetVpn = viewModel::setInternalWithVpn,
                        onSetPrioritize = viewModel::setPrioritize,
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
        activity?.title = getString(commonR.string.pref_connection_homenetwork)
        updateLocationStatus()
    }

    private fun updateLocationStatus() {
        val locationEnabled = DisabledLocationHandler.isLocationEnabled(requireContext())
        if (!locationEnabled) {
            canReadWifi = false
            return
        }

        val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        canReadWifi = checkPermission(permissionsToCheck)
    }

    private fun onRequestLocationPermission() {
        val permissionsToCheck: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (DisabledLocationHandler.isLocationEnabled(requireContext())) {
            LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(
                requireContext(),
                permissionsToCheck,
                continueYesCallback = {
                    requestLocationPermission()
                },
            )
        } else {
            DisabledLocationHandler.showLocationDisabledWarnDialog(
                requireActivity(),
                arrayOf(
                    getString(commonR.string.manage_ssids_wifi),
                ),
                showAsNotification = false,
            )
        }
    }

    private fun checkPermission(permissions: Array<String>?): Boolean {
        if (!permissions.isNullOrEmpty()) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
                    PackageManager.PERMISSION_DENIED
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun requestLocationPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) // Background location will be requested later
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionsRequest.launch(permissions)
    }

    private fun onPermissionsResult(results: Map<String, Boolean>) {
        if (results.keys.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            // For Android 11+ we MUST NOT request Background Location permission with fine or coarse
            // permissions as for Android 11 the background location request needs to be done separately
            // See here: https://developer.android.com/about/versions/11/privacy/location#request-background-location-separately
            // The separate request of background location is done here
            permissionsRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return
        }
        updateLocationStatus()
        viewModel.updateWifiState()
    }
}
