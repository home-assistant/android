package io.homeassistant.companion.android.sensors

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import androidx.core.os.postDelayed
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.contains
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorDao

class SensorDetailFragment(
    private val sensorManager: SensorManager,
    private val sensorId: String
) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            sensorManager: SensorManager,
            sensorId: String
        ): SensorDetailFragment {
            return SensorDetailFragment(sensorManager, sensorId)
        }
    }

    private lateinit var sensorDao: SensorDao
    private val handler = Handler()
    private val refresh = object : Runnable {
        override fun run() {
            refreshSensorData()
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        addPreferencesFromResource(R.xml.sensor_detail)

        findPreference<SwitchPreference>("enabled")?.let {
            val dao = sensorDao.get(sensorId)
            val perm = sensorManager.checkPermission(requireContext())
            if (dao == null) {
                it.isChecked = perm
            } else {
                it.isChecked = dao.enabled && perm
            }
            updateSensorEntity(it.isChecked)

            it.setOnPreferenceChangeListener { _, newState ->
                val isEnabled = newState as Boolean

                if (isEnabled && !sensorManager.checkPermission(requireContext())) {
                    requestPermissions(sensorManager.requiredPermissions(), 0)
                    return@setOnPreferenceChangeListener false
                }

                updateSensorEntity(isEnabled)
                this@SensorDetailFragment.refreshSensorData()

                return@setOnPreferenceChangeListener true
            }
        }
        findPreference<Preference>("description")?.let {
            var sensorDescription: String = when (sensorId) {
                "audio_sensor" -> resources.getString(R.string.audio_sensor)
                "battery_level" -> resources.getString(R.string.battery_level)
                "battery_state" -> resources.getString(R.string.battery_state)
                "bluetooth_connection" -> resources.getString(R.string.bluetooth_connection)
                "detected_activity" -> resources.getString(R.string.detected_activity)
                "dnd_sensor" -> resources.getString(R.string.dnd_sensor)
                "geocoded_location" -> resources.getString(R.string.geocoded_location)
                "last_reboot" -> resources.getString(R.string.last_reboot)
                "light_sensor" -> resources.getString(R.string.light_sensor)
                "location_background" -> resources.getString(R.string.pref_location_background_summary)
                "next_alarm" -> resources.getString(R.string.next_alarm)
                "phone_state" -> resources.getString(R.string.phone_state)
                "pressure_sensor" -> resources.getString(R.string.pressure_sensor)
                "proximity_sensor" -> resources.getString(R.string.proximity_sensor)
                "sim_1" -> resources.getString(R.string.sim_1)
                "sim_2" -> resources.getString(R.string.sim_2)
                "steps_sensor" -> resources.getString(R.string.steps_sensor)
                "storage_sensor" -> resources.getString(R.string.storage_sensor)
                "wifi_connection" -> resources.getString(R.string.wifi_connection)
                "zone_background" -> resources.getString(R.string.pref_location_zone_summary)
                else -> resources.getString(R.string.no_description)
            }
            it.summary = sensorDescription
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

    private fun refreshSensorData() {
        SensorWorker.start(requireContext())

        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        val fullData = sensorDao.getFull(sensorId)
        if (fullData?.sensor == null)
            return
        val sensorData = fullData.sensor
        val attributes = fullData.attributes

        findPreference<Preference>("unique_id")?.let {
            it.isCopyingEnabled = true
            it.summary = sensorId
        }
        findPreference<Preference>("state")?.let {
            it.isCopyingEnabled = true
            when {
                !sensorData.enabled ->
                    it.summary = "Disabled"
                sensorData.unitOfMeasurement.isNullOrBlank() ->
                    it.summary = sensorData.state
                else ->
                    it.summary = sensorData.state + " " + sensorData.unitOfMeasurement
            }
        }
        findPreference<Preference>("device_class")?.let {
            if (sensorData.deviceClass == null)
                it.isVisible = false
            else
                it.summary = sensorData.deviceClass

        }
        findPreference<Preference>("icon")?.let {
            if (sensorData.icon == "")
                it.isVisible = false
            else
                it.summary = sensorData.icon
        }

        findPreference<PreferenceCategory>("attributes")?.let {
            if (attributes.isNullOrEmpty())
                it.isVisible = false
            else {
                attributes.forEach { attribute ->
                    val key = "attribute_${attribute.name}"
                    val pref = findPreference(key) ?: Preference(requireContext())
                    pref.isCopyingEnabled = true
                    pref.key = key
                    pref.title = attribute.name
                    pref.summary = attribute.value
                    pref.isIconSpaceReserved = false

                    if (!it.contains(pref))
                        it.addPreference(pref)
                }
            }
        }
    }

    private fun updateSensorEntity(
        isEnabled: Boolean
    ) {
        val sensorEntity = sensorDao.get(sensorId)
        if (sensorEntity != null) {
            sensorEntity.enabled = isEnabled
            sensorDao.update(sensorEntity)
        }
        refreshSensorData()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        findPreference<SwitchPreference>("enabled")?.run {
            isChecked = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            updateSensorEntity(isChecked)
        }
    }
}
