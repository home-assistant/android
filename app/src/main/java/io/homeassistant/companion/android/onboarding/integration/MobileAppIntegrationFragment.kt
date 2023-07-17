package io.homeassistant.companion.android.onboarding.integration

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.notifications.NotificationPermissionFragment
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class MobileAppIntegrationFragment : Fragment() {

    private val requestLocationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onLocationPermissionResult(it)
    }

    private var dialog: AlertDialog? = null
    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    MobileAppIntegrationView(
                        onboardingViewModel = viewModel,
                        openPrivacyPolicy = this@MobileAppIntegrationFragment::openPrivacyPolicy,
                        onLocationTrackingChanged = this@MobileAppIntegrationFragment::onLocationTrackingChanged,
                        onFinishClicked = this@MobileAppIntegrationFragment::onComplete
                    )
                }
            }
        }
    }

    private fun onLocationTrackingChanged(isChecked: Boolean) {
        var checked = isChecked
        if (isChecked) {
            val locationEnabled = DisabledLocationHandler.isLocationEnabled(requireContext())
            val permissionOk = LocationSensorManager().checkPermission(
                requireContext(),
                LocationSensorManager.backgroundLocation.id
            )

            if (!locationEnabled) {
                DisabledLocationHandler.showLocationDisabledWarnDialog(
                    requireActivity(),
                    arrayOf(getString(LocationSensorManager.backgroundLocation.name))
                )
                checked = false
            } else if (!permissionOk) {
                dialog = AlertDialog.Builder(requireContext())
                    .setTitle(commonR.string.enable_location_tracking)
                    .setMessage(commonR.string.enable_location_tracking_prompt)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(LocationSensorManager.backgroundLocation.id)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .create()
                dialog?.show()
                checked = false
            }
        }

        viewModel.setLocationTracking(checked)
    }

    private fun requestPermissions(sensorId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestLocationPermissions.launch(
                LocationSensorManager().requiredPermissions(sensorId)
                    .toList().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    .toTypedArray()
            )
        } else {
            requestLocationPermissions.launch(LocationSensorManager().requiredPermissions(sensorId))
        }
    }

    private fun onLocationPermissionResult(
        results: Map<String, Boolean>
    ) {
        dialog?.dismiss()

        if (
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            requestLocationPermissions.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return
        }

        val hasPermission = results.values.all { it }
        viewModel.setLocationTracking(hasPermission)
        requestBackgroundAccess()
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        if (!isIgnoringBatteryOptimizations()) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${activity?.packageName}")
                )
            )
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
            context?.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(activity?.packageName ?: "")
                ?: false
    }

    private fun onComplete() {
        if (viewModel.notificationsPossible.value != viewModel.notificationsEnabled) {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, NotificationPermissionFragment::class.java, null)
                .addToBackStack(null)
                .commit()
        } else { // Complete onboarding
            activity?.setResult(Activity.RESULT_OK, viewModel.getOutput().toIntent())
            activity?.finish()
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(commonR.string.privacy_url)))
        startActivity(intent)
    }
}
