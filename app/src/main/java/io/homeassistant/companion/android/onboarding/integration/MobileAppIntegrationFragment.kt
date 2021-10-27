package io.homeassistant.companion.android.onboarding.integration

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.databinding.FragmentMobileAppIntegrationBinding
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.util.DisabledLocationHandler
import javax.inject.Inject

class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        private const val LOADING_VIEW = 1
        private const val ERROR_VIEW = 2

        private const val BACKGROUND_REQUEST = 99

        private const val LOCATION_REQUEST_CODE = 0

        private var dialog: AlertDialog? = null

        fun newInstance(): MobileAppIntegrationFragment {
            return MobileAppIntegrationFragment()
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter

    private var _binding: FragmentMobileAppIntegrationBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMobileAppIntegrationBinding.inflate(inflater, container, false)
        val context = binding.root.context

        binding.deviceName.setText(Build.MODEL)

        binding.locationTracking.let {
            val sensorId = LocationSensorManager.backgroundLocation.id
            setLocationTracking(it, DisabledLocationHandler.isLocationEnabled(context) && LocationSensorManager().checkPermission(context, sensorId))
            it.setOnCheckedChangeListener { _, isChecked ->
                var checked = isChecked
                if (isChecked) {

                    val locationEnabled = DisabledLocationHandler.isLocationEnabled(context)
                    val permissionOk = LocationSensorManager().checkPermission(requireContext(), sensorId)

                    if (!locationEnabled) {
                        DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), arrayOf(getString(LocationSensorManager.backgroundLocation.name)))
                        checked = false
                    } else if (!permissionOk) {
                        dialog = AlertDialog.Builder(requireContext())
                            .setTitle(R.string.enable_location_tracking)
                            .setMessage(R.string.enable_location_tracking_prompt)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                requestPermissions(
                                    sensorId
                                )
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .create()
                        dialog?.show()
                        checked = false
                    }
                }

                setLocationTracking(it, checked)
            }
        }

        binding.finish.setOnClickListener {
            presenter.onRegistrationAttempt(false, binding.deviceName.text.toString())
        }

        binding.retry.setOnClickListener {
            presenter.onRegistrationAttempt(false, binding.deviceName.text.toString())
        }

        return binding.root
    }

    override fun deviceRegistered() {
        (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
    }

    override fun showWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.firebase_error_title)
            .setMessage(R.string.firebase_error_message)
            .setPositiveButton(R.string.skip) { _, _ ->
                presenter.onRegistrationAttempt(true, binding.deviceName.text.toString())
            }
            .setNegativeButton(R.string.retry) { _, _ ->
                presenter.onRegistrationAttempt(false, binding.deviceName.text.toString())
            }
            .show()
    }

    override fun showError() {
        binding.viewFlipper.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        binding.viewFlipper.displayedChild = LOADING_VIEW
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        SensorWorker.start(requireContext())
        presenter.onFinish()
        super.onDestroy()
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
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), LOCATION_REQUEST_CODE)
        }

        if (requestCode == LOCATION_REQUEST_CODE) {
            val hasPermission = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            setLocationTracking(binding.locationTracking, hasPermission)
            requestBackgroundAccess()
        }
    }

    private fun setLocationTracking(locationTrackingSwitch: SwitchMaterial, enabled: Boolean) {
        locationTrackingSwitch.isChecked = enabled

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
}
