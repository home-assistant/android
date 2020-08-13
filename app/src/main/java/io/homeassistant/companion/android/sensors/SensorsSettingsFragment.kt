package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

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

        ioScope.launch {
            val managers = SensorReceiver.MANAGERS.plus(LocationBroadcastReceiver())

            managers.sortedBy { it.name }.forEach { manager ->
                val prefCategory = PreferenceCategory(preferenceScreen.context)
                prefCategory.title = manager.name
                preferenceScreen.addPreference(prefCategory)
                manager.getSensorRegistrations(requireContext()).sortedBy { it.name }
                    .forEach { sensor ->
                    val pref = Preference(preferenceScreen.context)
                    pref.title = sensor.name

                    if (sensor.unitOfMeasurement.isNullOrBlank())
                        pref.summary = sensor.state.toString()
                    else
                        pref.summary = sensor.state.toString() + " " + sensor.unitOfMeasurement

                    // TODO: Add the icon from mdi:icon?

                    pref.setOnPreferenceClickListener {
                        parentFragmentManager
                            .beginTransaction()
                            .replace(
                                R.id.content,
                                SensorDetailFragment.newInstance(
                                    manager,
                                    sensor
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
    }
}
