package io.homeassistant.companion.android.settings

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.settings.ssid.SsidPreference
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    companion object {
        private const val SSID_DIALOG_TAG = "${BuildConfig.APPLICATION_ID}.SSID_DIALOG_TAG"

        fun newInstance() = SettingsFragment()
    }

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

        val onChangeBiometricValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            var isValid: Boolean
            if (newValue == false)
                isValid = true
            else {
                isValid = true
                if (BiometricManager.from(activity!!).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
                    promptForUnlock()
                else {
                    isValid = false
                    AlertDialog.Builder(activity!!)
                        .setTitle(R.string.set_lock_title)
                        .setMessage(R.string.set_lock_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
            }
            isValid
        }

        findPreference<EditTextPreference>("connection_internal")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("connection_external")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<SwitchPreference>("app_lock")?.onPreferenceChangeListener =
            onChangeBiometricValidator

        findPreference<Preference>("version")?.let {
            it.summary = BuildConfig.VERSION_NAME
        }

        presenter.onCreate()
    }

    override fun onLocationSettingChanged() {
        if (!PermissionManager.hasLocationPermissions(context!!)) {
            PermissionManager.requestLocationPermissions(this)
        }
        PermissionManager.restartLocationTracking(context!!)
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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SsidPreference) {
            // check if dialog is already showing
            val fm = fragmentManager
            if (fm == null || fm.findFragmentByTag(SSID_DIALOG_TAG) != null) {
                return
            }
            val ssidDialog = SsidDialogFragment.newInstance("connection_internal_ssids")
            ssidDialog.setTargetFragment(this, 0)
            ssidDialog.show(fm, SSID_DIALOG_TAG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionManager.validateLocationPermissions(requestCode, permissions, grantResults)) {
            PermissionManager.restartLocationTracking(context!!)
        } else {
            // If we don't have permissions, don't let them in!
            findPreference<SwitchPreference>("location_zone")!!.isChecked = false
            findPreference<SwitchPreference>("location_background")!!.isChecked = false
        }
    }

    private fun promptForUnlock() {
        val executor = ContextCompat.getMainExecutor(activity!!)
        val switchLock = findPreference<SwitchPreference>("app_lock")
        val biometricPrompt = BiometricPrompt(activity!!, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    switchLock?.isChecked = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    switchLock?.isChecked = false
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity!!.resources.getString(R.string.biometric_title))
            .setSubtitle(activity!!.resources.getString(R.string.biometric_message))
            .setDeviceCredentialAllowed(true)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
