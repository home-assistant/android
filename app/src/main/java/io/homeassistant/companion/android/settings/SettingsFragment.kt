package io.homeassistant.companion.android.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
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
import io.homeassistant.companion.android.sensors.SensorWorker
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

        val onChangeTimeOutValidator = Preference.OnPreferenceChangeListener { _, _ ->
            var result = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                if (!Settings.System.canWrite(this.requireContext())) {
                    AlertDialog.Builder(this.requireContext())
                        .setTitle(R.string.write_request_title)
                        .setMessage(R.string.write_request_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${activity?.packageName}")
                                )
                            )
                            result = true
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            result = false
                        }
                        .show()
                }
            result
        }

        findPreference<EditTextPreference>("connection_internal")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("connection_external")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("dim_screen")?.onPreferenceChangeListener =
            onChangeTimeOutValidator

        findPreference<EditTextPreference>("dim_screen")?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

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

    override fun restartSensorWorker() {
        SensorWorker.start(context!!)
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
