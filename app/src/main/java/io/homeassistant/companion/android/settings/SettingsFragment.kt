package io.homeassistant.companion.android.settings

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    @Inject
    lateinit var presenter: SettingsPresenter

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences, rootKey)

        val onChangeUrlValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            val isValid = newValue.toString().isBlank() || newValue.toString().toHttpUrlOrNull() != null
            if (!isValid) {
                AlertDialog.Builder(activity!!)
                    .setTitle(R.string.url_invalid)
                    .setMessage(R.string.url_parse_error)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
            isValid
        }

        findPreference<EditTextPreference>("connection_internal")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("connection_external")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<Preference>("version")?.let {
            it.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }

        presenter.onCreate()
    }

    override fun onLocationSettingChanged() {
        if (!PermissionManager.hasLocationPermissions(context!!)) {
            PermissionManager.requestLocationPermissions(this)
        }
        PermissionManager.restartLocationTracking(context!!, activity!!)
    }

    override fun disableInternalConnection() {
        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = false
        }
    }

    override fun enableInternalConnection() {
        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionManager.validateLocationPermissions(requestCode, permissions, grantResults)) {
            PermissionManager.restartLocationTracking(context!!, activity!!)
        } else {
            // If we don't have permissions, don't let them in!
            findPreference<SwitchPreference>("location_zone")!!.isChecked = false
            findPreference<SwitchPreference>("location_background")!!.isChecked = false
        }
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
