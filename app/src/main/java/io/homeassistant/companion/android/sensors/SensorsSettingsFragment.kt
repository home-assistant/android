package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.util.LocationPermissionInfoHandler
import javax.inject.Inject

class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            SensorWorker.start(requireContext())
            totalDisabledSensors = 0
            totalEnabledSensors = 0
            val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
            SensorReceiver.MANAGERS.forEach { managers ->
                managers.availableSensors.forEach { basicSensor ->
                    findPreference<Preference>(basicSensor.id)?.let {
                        val sensorEntity = sensorDao.get(basicSensor.id)
                        if (sensorEntity?.enabled == true) {
                            totalEnabledSensors += 1
                            if (basicSensor.unitOfMeasurement.isNullOrBlank())
                                it.summary = sensorEntity.state
                            else
                                it.summary = sensorEntity.state + " " + basicSensor.unitOfMeasurement
                            // TODO: Add the icon from mdi:icon?
                        } else {
                            totalDisabledSensors += 1
                            it.summary = "Disabled"
                        }
                    }
                }
            }

            findPreference<PreferenceCategory>("enable_disable_category")?.let {
                it.summary = getString(R.string.manage_all_sensors_summary, (totalDisabledSensors + totalEnabledSensors))
            }

            findPreference<SwitchPreference>("enable_disable_sensors")?.let {
                if (totalDisabledSensors == 0) {
                    it.title = getString(R.string.disable_all_sensors, totalEnabledSensors)
                    it.summary = ""
                    it.isChecked = permissionsAllGranted
                } else {
                    if (totalEnabledSensors == 0)
                        it.title = getString(R.string.enable_all_sensors)
                    else
                        it.title = getString(R.string.enable_remaining_sensors, totalDisabledSensors)
                    it.summary = getString(R.string.enable_all_sensors_summary)
                    it.isChecked = false
                }
            }

            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        private var totalEnabledSensors = 0
        private var totalDisabledSensors = 0
        private var permissionsAllGranted = true
        private var settingsWithLocation = mutableListOf<String>()
        private var enableAllSensors = false

        fun newInstance(): SensorsSettingsFragment {
            return SensorsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        setPreferencesFromResource(R.xml.sensors, rootKey)

        findPreference<SwitchPreference>("enable_disable_sensors")?.let {

            it.setOnPreferenceChangeListener { _, newState ->

                settingsWithLocation.clear()
                var permArray: Array<String> = arrayOf()
                val context = requireContext()
                enableAllSensors = newState as Boolean

                val locationEnabledFine = DisabledLocationHandler.isLocationEnabled(context, true)
                val locationEnabledCoarse = DisabledLocationHandler.isLocationEnabled(context, false)

                SensorReceiver.MANAGERS.forEach { managers ->
                    managers.availableSensors.forEach { basicSensor ->
                        val requiredPermissions = managers.requiredPermissions(basicSensor.id)

                        val locationPermissionCoarse = DisabledLocationHandler.containsLocationPermission(requiredPermissions, false)
                        val locationPermissionFine = DisabledLocationHandler.containsLocationPermission(requiredPermissions, true)
                        val locationPermission = locationPermissionCoarse || locationPermissionFine

                        var enableSensor = false
                        // Only if one of the options is true, enable the sensor
                        if (!enableAllSensors || // All Sensors switch is disabled
                            !locationPermission || // No location permission used for sensor
                            (locationEnabledCoarse && locationPermissionCoarse) || // Coarse location used for sensor and location coarse is enabled in settings
                            (locationEnabledFine && locationPermissionFine) // Fine location used for sensor and location fine is enabled in settings
                        ) {
                            enableSensor = enableAllSensors
                        } else {
                            settingsWithLocation.add(getString(basicSensor.name))
                        }

                        if (enableSensor && !managers.checkPermission(requireContext(), basicSensor.id))
                            permArray += requiredPermissions.asList()
                    }
                }

                if (permArray.isNotEmpty()) {

                    if (enableAllSensors) {
                        val locationPermissionCoarse = DisabledLocationHandler.containsLocationPermission(permArray, false)
                        val locationPermissionFine = DisabledLocationHandler.containsLocationPermission(permArray, true)
                        if (locationPermissionCoarse || locationPermissionFine) {
                            LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(context, permArray, continueYesCallback = {
                                requestPermission(permArray)
                            })
                        } else requestPermission(permArray)
                    }
                } else {

                    if (showAdditionalDialogsIfNeeded()) {
                        return@setOnPreferenceChangeListener false
                    }
                }

                return@setOnPreferenceChangeListener true
            }
        }

        SensorReceiver.MANAGERS.sortedBy { getString(it.name) }.filter { it.hasSensor(requireContext()) }.forEach { manager ->
            val prefCategory = PreferenceCategory(preferenceScreen.context)
            prefCategory.title = getString(manager.name)

            preferenceScreen.addPreference(prefCategory)
            manager.availableSensors.sortedBy { getString(it.name) }.forEach { basicSensor ->

                val pref = Preference(preferenceScreen.context)
                pref.key = basicSensor.id
                pref.title = getString(basicSensor.name)

                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            SensorDetailFragment.newInstance(
                                manager,
                                basicSensor
                            )
                        )
                        .addToBackStack("Sensor Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory.addPreference(pref)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refresh, 0)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0) {
            enableDisableSensorBasedOnPermission()
            showDisabledLocationWarningIfNeeded()
        }
    }

    private fun requestPermission(permissions: Array<String>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestPermissions(
                permissions.toSet()
                    .minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    .toTypedArray(), 0
            )
        } else {
            requestPermissions(permissions, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
            return
        }

        showAdditionalDialogsIfNeeded()

        findPreference<SwitchPreference>("enable_disable_sensors")?.run {
            permissionsAllGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            this.isChecked = permissionsAllGranted
        }
    }

    private fun showAdditionalDialogsIfNeeded(): Boolean {
        return if (!showNotificationListenerDialogIfNeeded()) {
            enableDisableSensorBasedOnPermission()
            showDisabledLocationWarningIfNeeded()
        } else true
    }

    private fun showNotificationListenerDialogIfNeeded(): Boolean {
        val context = requireContext()
        return if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), 0)
            true
        } else false
    }

    private fun showDisabledLocationWarningIfNeeded(): Boolean {
        return if (settingsWithLocation.isNotEmpty()) {
            DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), settingsWithLocation.toTypedArray())
            true
        } else false
    }

    private fun enableDisableSensorBasedOnPermission() {
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        SensorReceiver.MANAGERS.forEach { managers ->
            managers.availableSensors.forEach { basicSensor ->
                val sensorTurnsOnWithGroupToggle = managers.enableToggleAll(requireContext(), basicSensor.id)
                if (!sensorTurnsOnWithGroupToggle) {
                    return@forEach
                }
                var enableSensor = false
                if (enableAllSensors && sensorTurnsOnWithGroupToggle) {
                    enableSensor = managers.checkPermission(requireContext(), basicSensor.id)
                }
                var sensorEntity = sensorDao.get(basicSensor.id)
                if (sensorEntity != null) {
                    sensorEntity.enabled = enableSensor
                    sensorEntity.lastSentState = ""
                    sensorDao.update(sensorEntity)
                } else {
                    sensorEntity = Sensor(basicSensor.id, enableSensor, false, "")
                    sensorDao.add(sensorEntity)
                }
            }
        }
    }
}
