package io.homeassistant.companion.android.onboarding.integration

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class MobileAppIntegrationFragment : Fragment() {

    companion object {
        private const val BACKGROUND_REQUEST = 99

        private const val LOCATION_REQUEST_CODE = 0
    }

    private var dialog: AlertDialog? = null
    private val viewModel by activityViewModels<OnboardingViewModel>()

    @ExperimentalComposeUiApi
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

        setLocationTracking(checked)
        viewModel.locationTrackingEnabled.value = checked
    }

    private fun requestPermissions(sensorId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this@MobileAppIntegrationFragment.requestPermissions(
                LocationSensorManager().requiredPermissions(sensorId)
                    .toList().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    .toTypedArray(),
                LOCATION_REQUEST_CODE
            )
        } else {
            this@MobileAppIntegrationFragment.requestPermissions(
                LocationSensorManager().requiredPermissions(sensorId),
                LOCATION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        dialog?.dismiss()

        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }

        if (requestCode == LOCATION_REQUEST_CODE) {
            val hasPermission = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            viewModel.locationTrackingEnabled.value = hasPermission
            setLocationTracking(hasPermission)
            requestBackgroundAccess()
        }
    }

    private fun setLocationTracking(enabled: Boolean) {
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        arrayOf(
            LocationSensorManager.backgroundLocation,
            LocationSensorManager.zoneLocation,
            LocationSensorManager.singleAccurateLocation
        ).forEach { basicSensor ->
            var sensorEntity = sensorDao.get(basicSensor.id)
            if (sensorEntity != null) {
                sensorEntity.enabled = enabled
                sensorEntity.lastSentState = ""
                sensorDao.update(sensorEntity)
            } else {
                sensorEntity = Sensor(basicSensor.id, enabled, false, "")
                sensorDao.add(sensorEntity)
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        val intent: Intent
        if (!isIgnoringBatteryOptimizations()) {
            intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity?.packageName}")
            )
            startActivityForResult(intent, BACKGROUND_REQUEST)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
            context?.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(activity?.packageName ?: "")
                ?: false
    }

    private fun onComplete() {
        val retData = Intent().apply {
            putExtra("URL", viewModel.manualUrl.value)
            putExtra("AuthCode", viewModel.authCode.value)
            putExtra("DeviceName", viewModel.deviceName.value)
            putExtra("LocationTracking", viewModel.locationTrackingEnabled.value)
        }
        activity?.setResult(Activity.RESULT_OK, retData)
        activity?.finish()
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(commonR.string.privacy_url)))
        startActivity(intent)
    }
}
