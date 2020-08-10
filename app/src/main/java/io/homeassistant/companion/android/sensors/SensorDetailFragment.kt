package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class SensorDetailFragment(private val sensorRegistration: SensorRegistration<Any>) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(sensorRegistration: SensorRegistration<Any>): SensorDetailFragment {
            return SensorDetailFragment(sensorRegistration)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        addPreferencesFromResource(R.xml.sensor_detail)

        findPreference<SwitchPreference>("enabled")?.setOnPreferenceChangeListener { _, newValue ->
            TODO("Add something to save enabled/disabled state.")
            true
        }

        findPreference<Preference>("unique_id")?.let {
            it.summary = sensorRegistration.uniqueId
        }
        findPreference<Preference>("state")?.let {
            if (sensorRegistration.unitOfMeasurement.isNullOrBlank())
                it.summary = sensorRegistration.state.toString()
            else
                it.summary =
                    sensorRegistration.state.toString() + " " + sensorRegistration.unitOfMeasurement
        }
        findPreference<Preference>("device_class")?.let {
            it.summary = sensorRegistration.deviceClass
        }
        findPreference<Preference>("icon")?.let {
            it.summary = sensorRegistration.icon
        }

        findPreference<PreferenceCategory>("attributes")?.let {
            if(sensorRegistration.attributes.isEmpty())
                it.isVisible = false
            else {
                sensorRegistration.attributes.entries.forEach { attribue ->
                    val pref = Preference(requireContext())
                    pref.title = attribue.key
                    pref.summary = attribue.value.toString()

                    it.addPreference(pref)
                }
            }
        }
    }
}