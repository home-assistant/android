package io.homeassistant.companion.android.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    @Inject
    lateinit var presenter: SettingsPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<SwitchPreference>("location_zone")!!.setOnPreferenceChangeListener {
                _, newValue ->
            presenter.onLocationZoneChange(newValue as Boolean)

            true
        }

        findPreference<SwitchPreference>("location_background")!!.setOnPreferenceChangeListener {
                _, newValue ->
            presenter.onLocationBackgroundChange(newValue as Boolean)

            true
        }

        findPreference<Preference>("version").let {
            it!!.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
    }

    override fun onLocationSettingChanged() {
        val intent = Intent(context, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

        activity!!.sendBroadcast(intent)
    }

    companion object {
        @JvmStatic
        fun newInstance() = SettingsFragment()
    }
}
