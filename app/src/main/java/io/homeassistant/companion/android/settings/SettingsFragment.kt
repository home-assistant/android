package io.homeassistant.companion.android.settings

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricManager
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.sensors.SensorsSettingsFragment
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.settings.ssid.SsidPreference
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    companion object {
        private const val SSID_DIALOG_TAG = "${BuildConfig.APPLICATION_ID}.SSID_DIALOG_TAG"

        fun newInstance() = SettingsFragment()
    }

    @Inject
    lateinit var presenter: SettingsPresenter
    private lateinit var authenticator: Authenticator
    private var setLock = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        authenticator = Authenticator(requireContext(), requireActivity(), ::authenticationResult)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences, rootKey)

        val onChangeUrlValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            val isValid = newValue.toString().isBlank() || newValue.toString().toHttpUrlOrNull() != null
            if (!isValid) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.url_invalid)
                    .setMessage(R.string.url_parse_error)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
            isValid
        }

        findPreference<SwitchPreference>("app_lock")?.setOnPreferenceChangeListener { _, newValue ->
            var isValid: Boolean
            if (newValue == false) {
                isValid = true
                findPreference<EditTextPreference>("session_timeout")?.isVisible = false
            } else {
                isValid = true
                if (BiometricManager.from(requireActivity()).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                    setLock = true
                    authenticator.title = getString(R.string.biometric_set_title)
                    authenticator.authenticate()
                } else {
                    isValid = false
                    AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.set_lock_title)
                        .setMessage(R.string.set_lock_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
            }
            isValid
        }

        findPreference<EditTextPreference>("session_timeout")?.let { pref ->
            pref.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref.isVisible = findPreference<SwitchPreference>("app_lock")?.isChecked == true
        }

        findPreference<Preference>("nfc_tags")?.let {
            it.isVisible = presenter.nfcEnabled()
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(NfcSetupActivity.newInstance(requireActivity()))
                true
            }
        }

        removeSystemFromThemesIfNeeded()

        findPreference<EditTextPreference>("connection_internal")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("connection_external")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<Preference>("sensors")?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, SensorsSettingsFragment.newInstance())
                .addToBackStack(getString(R.string.sensors))
                .commit()
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("version")?.let {
            it.isCopyingEnabled = true
            it.summary = BuildConfig.VERSION_NAME
        }

        presenter.onCreate()
    }

    override fun disableInternalConnection() {
        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = false
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_computer)
                unwrappedDrawable?.setTint(Color.DKGRAY)
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Log.d("SettingsFragment", "Unable to set the icon tint", e)
            }
        }
    }

    override fun enableInternalConnection() {
        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = true
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_computer)
                unwrappedDrawable?.setTint(resources.getColor(R.color.colorAccent))
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Log.d("SettingsFragment", "Unable to set the icon tint", e)
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SsidPreference) {
            // check if dialog is already showing
            val fm = parentFragmentManager
            if (fm.findFragmentByTag(SSID_DIALOG_TAG) != null) {
                return
            }
            val ssidDialog = SsidDialogFragment.newInstance("connection_internal_ssids")
            ssidDialog.setTargetFragment(this, 0)
            ssidDialog.show(fm, SSID_DIALOG_TAG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun authenticationResult(result: Int) {
        val success = result == Authenticator.SUCCESS
        val switchLock = findPreference<SwitchPreference>("app_lock")
        switchLock?.isChecked = success
        findPreference<EditTextPreference>("session_timeout")?.isVisible = success
    }

    private fun removeSystemFromThemesIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val pref = findPreference<ListPreference>("themes")
            if (pref != null) {
                val systemIndex = pref.findIndexOfValue("system")
                if (systemIndex > 0) {
                    var entries = pref.entries?.toMutableList()
                    entries?.removeAt(systemIndex)
                    var entryValues = pref.entryValues?.toMutableList()
                    entryValues?.removeAt(systemIndex)
                    if (entries != null && entryValues != null) {
                        pref.entries = entries.toTypedArray()
                        pref.entryValues = entryValues.toTypedArray()
                    }
                }
            }
        }
    }
}
