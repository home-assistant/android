package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
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
                if (!permissionsAllGranted) {
                    it.title = getString(R.string.enable_all_sensors)
                    it.summary = getString(R.string.enable_all_sensors_summary)
                }
            }

            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        private var totalEnabledSensors = 0
        private var totalDisabledSensors = 0
        private var permissionsAllGranted = true
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

            var permArray: Array<String> = arrayOf()
            it.setOnPreferenceChangeListener { _, newState ->
                val enabledAll = newState as Boolean
                val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

                SensorReceiver.MANAGERS.forEach { managers ->
                    managers.availableSensors.forEach { basicSensor ->
                        var sensorEntity = sensorDao.get(basicSensor.id)

                        if (!managers.checkPermission(requireContext(), basicSensor.id))
                            permArray += managers.requiredPermissions(basicSensor.id).asList()

                        if (sensorEntity != null) {
                            sensorEntity.enabled = enabledAll
                            sensorEntity.lastSentState = ""
                            sensorDao.update(sensorEntity)
                        } else {
                            sensorEntity = Sensor(basicSensor.id, enabledAll, false, "")
                            sensorDao.add(sensorEntity)
                        }
                    }
                }
                if (!permArray.isNullOrEmpty())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        requestPermissions(permArray.toSet()
                            .minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            .toTypedArray(), 0)
                    } else {
                        requestPermissions(permArray, 0)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
            return
        }

        if (permissions.any { perm -> perm == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE })
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

        findPreference<SwitchPreference>("enable_disable_sensors")?.run {
            permissionsAllGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            this.isChecked = permissionsAllGranted
        }
    }
}
