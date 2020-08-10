package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class SensorsSettingsFragment: PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    lateinit var allSensorsUpdater: AllSensorsUpdater

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

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

        allSensorsUpdater = AllSensorsUpdaterImpl(integrationUseCase, requireContext())

        setPreferencesFromResource(R.xml.sensors, rootKey)

        ioScope.launch {
            val managers = allSensorsUpdater.getManagers()
            val preferences = mutableListOf<Preference>()

            managers.forEach { manager ->
                manager.getSensorRegistrations(requireContext()).forEach { sensor ->
                    val pref = Preference(context)
                    pref.title = sensor.name

                    if(sensor.unitOfMeasurement.isNullOrBlank())
                        pref.summary = sensor.state.toString()
                    else
                        pref.summary = sensor.state.toString() + " " + sensor.unitOfMeasurement

                    //TODO: Add the icon from mdi:icon?

                    pref.setOnPreferenceClickListener {
                        parentFragmentManager
                            .beginTransaction()
                            .replace(R.id.content, SensorDetailFragment.newInstance(sensor, manager.requiredPermissions()))
                            .addToBackStack("Sensor Detail")
                            .commit()
                        return@setOnPreferenceClickListener true
                    }

                    preferences.add(pref)
                }
            }

            preferences.sortBy { it.title.toString() }
            preferences.forEach { preferenceScreen.addPreference(it) }
        }
    }
}