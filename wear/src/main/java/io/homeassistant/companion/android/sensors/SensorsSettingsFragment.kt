package io.homeassistant.companion.android.sensors

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.forEach
import androidx.preference.iterator
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

// TODO remove me
@AndroidEntryPoint
class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            SensorWorker.start(requireContext())
            val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
            SensorReceiver.MANAGERS.forEach { managers ->
                managers.getAvailableSensors(requireContext()).forEach { basicSensor ->
                    findPreference<Preference>(basicSensor.id)?.let {
                        val sensorEntity = sensorDao.get(basicSensor.id)
                        if (sensorEntity?.enabled == true) {
                            if (!sensorEntity.state.isNullOrBlank()) {
                                if (basicSensor.unitOfMeasurement.isNullOrBlank()) it.summary = sensorEntity.state
                                else it.summary = sensorEntity.state + " " + basicSensor.unitOfMeasurement
                            } else {
                                it.summary = getString(commonR.string.enabled)
                            }
                            // TODO: Add the icon from mdi:icon?
                        } else {
                            it.summary = getString(commonR.string.disabled)
                        }
                    }
                }
            }
            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        private var showOnlyEnabledSensors = false
        private const val TAG = "SensorSettings"

        fun newInstance(): SensorsSettingsFragment {
            return SensorsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.sensors, rootKey)

        SensorReceiver.MANAGERS.sortedBy { getString(it.name) }.filter { it.hasSensor(requireContext()) }.forEach { manager ->
            val prefCategory = PreferenceCategory(preferenceScreen.context)
            prefCategory.title = getString(manager.name)
            preferenceScreen.addPreference(prefCategory)

            manager.getAvailableSensors(requireContext()).sortedBy { getString(it.name) }.forEach { basicSensor ->
                val pref = Preference(preferenceScreen.context)
                pref.key = basicSensor.id
                pref.title = getString(basicSensor.name)
                pref.isVisible = !showOnlyEnabledSensors || (showOnlyEnabledSensors && manager.isEnabled(requireContext(), basicSensor.id))
                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            SensorDetailFragment.newInstance(
                                manager,
                                basicSensor,
                                integrationUseCase
                            )
                        )
                        .addToBackStack("Sensor Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory.addPreference(pref)
            }
        }

        if (showOnlyEnabledSensors) showHideGroupsIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Needed to call onPrepareOptionsMenu
        setHasOptionsMenu(true)
    }

    private fun filterSensors(searchQuery: String? = "") {
        SensorReceiver.MANAGERS.filter { m -> m.hasSensor(requireContext()) }.forEach { manager ->
            manager.getAvailableSensors(requireContext()).forEach { sensor ->
                val pref = findPreference<Preference>(sensor.id)
                if (pref != null) {
                    pref.isVisible = true
                    if (!searchQuery.isNullOrBlank()) {
                        val found = pref.title.contains(searchQuery, true)
                        pref.isVisible = found
                    } else {
                        if (showOnlyEnabledSensors) {
                            pref.isVisible = manager.isEnabled(requireContext(), sensor.id)
                        }
                    }
                }
            }
        }
        showHideGroupsIfNeeded()
    }

    private fun showHideGroupsIfNeeded() {
        for (pref in preferenceScreen) {
            val prefGroup = pref as? PreferenceGroup
            if (prefGroup != null) {
                prefGroup.isVisible = false

                prefGroup.forEach { pref ->
                    if (pref.isVisible) {
                        prefGroup.isVisible = true
                        return@forEach
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.sensors)
        filterSensors()
        handler.postDelayed(refresh, 0)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }
}
