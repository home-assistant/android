package io.homeassistant.companion.android.onboarding.integration

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.maybeAskForIgnoringBatteryOptimizations
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.notifications.NotificationPermissionFragment
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import java.io.IOException
import java.security.KeyStore
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MobileAppIntegrationFragment : Fragment() {

    private val requestLocationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onLocationPermissionResult(it)
        }
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) {
        onGetContentResult(it)
    }

    private var dialog: AlertDialog? = null
    private val viewModel by activityViewModels<OnboardingViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    MobileAppIntegrationView(
                        onboardingViewModel = viewModel,
                        openPrivacyPolicy = this@MobileAppIntegrationFragment::openPrivacyPolicy,
                        onLocationTrackingChanged = this@MobileAppIntegrationFragment::onLocationTrackingChanged,
                        onSelectTLSCertificateClicked =
                        this@MobileAppIntegrationFragment::onSelectTLSCertificateClicked,
                        onCheckPassword = this@MobileAppIntegrationFragment::onCheckTLSCertificatePassword,
                        onFinishClicked = this@MobileAppIntegrationFragment::onComplete,
                    )
                }
            }
        }
    }

    private fun onLocationTrackingChanged(isChecked: Boolean) {
        lifecycleScope.launch {
            var checked = isChecked
            if (isChecked) {
                val locationEnabled = DisabledLocationHandler.isLocationEnabled(requireContext())
                val permissionOk = LocationSensorManager().checkPermission(
                    requireContext(),
                    LocationSensorManager.backgroundLocation.id,
                )

                if (!locationEnabled) {
                    DisabledLocationHandler.showLocationDisabledWarnDialog(
                        requireActivity(),
                        arrayOf(getString(LocationSensorManager.backgroundLocation.name)),
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
    }

    private fun onSelectTLSCertificateClicked() {
        getContent.launch("*/*")
    }

    private fun onCheckTLSCertificatePassword(password: String) {
        lifecycleScope.launch {
            var ok: Boolean
            context?.contentResolver?.openInputStream(viewModel.tlsClientCertificateUri!!)!!.buffered().use {
                val keystore = KeyStore.getInstance("PKCS12")
                ok = try {
                    keystore.load(it, password.toCharArray())
                    true
                } catch (e: IOException) {
                    // we cannot determine if it failed due to wrong password or other reasons, since e.cause is not set to UnrecoverableKeyException
                    false
                }
            }
            viewModel.tlsClientCertificatePasswordCorrect = ok
        }
    }

    @SuppressLint("Range")
    private fun onGetContentResult(uri: Uri?) {
        if (uri != null) {
            context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()

                viewModel.tlsClientCertificateUri = uri
                viewModel.tlsClientCertificateFilename =
                    cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
            // check with empty password
            onCheckTLSCertificatePassword("")
        }
    }

    private fun requestPermissions(sensorId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestLocationPermissions.launch(
                LocationSensorManager().requiredPermissions(requireContext(), sensorId)
                    .toList().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    .toTypedArray(),
            )
        } else {
            requestLocationPermissions.launch(LocationSensorManager().requiredPermissions(requireContext(), sensorId))
        }
    }

    private fun onLocationPermissionResult(results: Map<String, Boolean>) {
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
        context?.maybeAskForIgnoringBatteryOptimizations()
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
        val intent = Intent(Intent.ACTION_VIEW, getString(commonR.string.privacy_url).toUri())
        startActivity(intent)
    }
}
