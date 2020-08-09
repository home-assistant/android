package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
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

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        fun newInstance(): SensorsSettingsFragment {
            return SensorsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        allSensorsUpdater = AllSensorsUpdaterImpl(integrationUseCase, requireContext())

        setPreferencesFromResource(R.xml.sensors, rootKey)

        mainScope.launch {
            val managers = allSensorsUpdater.getManagers()

            managers.forEach { manager ->
                manager.getSensorRegistrations(requireContext()).forEach { sensor ->
                    val pref = Preference(context)
                    pref.title = sensor.name
                    preferenceScreen.addPreference(pref)
                }
            }
        }
    }
}