package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject

class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    companion object {
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

        val managers = SensorReceiver.MANAGERS.plus(LocationBroadcastReceiver())

        managers.sortedBy { it.name }.forEach { manager ->
            val prefCategory = PreferenceCategory(preferenceScreen.context)
            prefCategory.title = manager.name
            preferenceScreen.addPreference(prefCategory)
            manager.availableSensors.sortedBy { it.name }.forEach { basicSensor ->

                val pref = Preference(preferenceScreen.context)
                pref.key = basicSensor.id
                pref.title = basicSensor.name

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
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        SensorReceiver.MANAGERS.plus(LocationBroadcastReceiver()).forEach { managers ->
            managers.availableSensors.forEach { basicSensor ->
                findPreference<Preference>(basicSensor.id)?.let {
                    val sensorEntity = sensorDao.get(basicSensor.id)
                    if (sensorEntity?.enabled == true) {
                        if (basicSensor.unitOfMeasurement.isNullOrBlank())
                            it.summary = sensorEntity.state
                        else
                            it.summary = sensorEntity.state + " " + basicSensor.unitOfMeasurement
                        // TODO: Add the icon from mdi:icon?
                    } else {
                        it.summary = "Disabled"
                    }
                }
            }
        }
    }
}
