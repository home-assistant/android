package io.homeassistant.companion.android.sensors

import android.content.pm.PackageManager
import android.os.Bundle
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
    private val basicSensor: SensorManager.BasicSensor
) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            sensorManager: SensorManager,
            basicSensor: SensorManager.BasicSensor
        ): SensorDetailFragment {
            return SensorDetailFragment(sensorManager, basicSensor)
        }
    }

    private lateinit var sensorDao: SensorDao

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        addPreferencesFromResource(R.xml.sensor_detail)

        findPreference<SwitchPreference>("enabled")?.let {
            val dao = sensorDao.get(basicSensor.id)
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

        refreshSensorData()
    }

    private fun refreshSensorData() {
        val sensorData = sensorManager.getEnabledSensorData(requireContext(), basicSensor.id)

        findPreference<Preference>("unique_id")?.let {
            it.isCopyingEnabled = true
            it.summary = basicSensor.id
        }
        findPreference<Preference>("state")?.let {
            it.isCopyingEnabled = true
            when {
                sensorData == null ->
                    it.summary = "Disabled"
                basicSensor.unitOfMeasurement.isNullOrBlank() ->
                    it.summary = sensorData.state.toString()
                else ->
                    it.summary = sensorData.state.toString() + " " + basicSensor.unitOfMeasurement
            }
        }
        findPreference<Preference>("device_class")?.let {
            it.isCopyingEnabled = true
            it.summary = basicSensor.deviceClass
        }
        findPreference<Preference>("icon")?.let {
            it.isCopyingEnabled = true
            it.summary = sensorData?.icon ?: ""
        }

        findPreference<PreferenceCategory>("attributes")?.let {
            if (sensorData?.attributes.isNullOrEmpty())
                it.isVisible = false
            else {
                sensorData?.attributes?.keys?.forEach { key ->
                    val pref = findPreference("attribute_$key") ?: Preference(requireContext())
                    pref.isCopyingEnabled = true
                    pref.key = "attribute_$key"
                    pref.title = key
                    pref.summary = sensorData.attributes[key]?.toString() ?: ""
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
        val sensorEntity = sensorDao.get(basicSensor.id)
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
