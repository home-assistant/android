package io.homeassistant.companion.android.settings

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.settings.shortcuts.ShortcutsFragment
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

        val onChangeBiometricValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            var isValid: Boolean
            if (newValue == false)
                isValid = true
            else {
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

        val onClickShortcuts = Preference.OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, ShortcutsFragment.newInstance())
                .addToBackStack(getString(R.string.shortcuts))
                .commit()
            true
        }

        findPreference<EditTextPreference>("connection_internal")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<EditTextPreference>("connection_external")?.onPreferenceChangeListener =
            onChangeUrlValidator

        findPreference<SwitchPreference>("app_lock")?.onPreferenceChangeListener =
            onChangeBiometricValidator

        val shortcuts = findPreference<Preference>("shortcuts")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && presenter.getPanels().isNotEmpty()) {
            shortcuts?.onPreferenceClickListener =
                onClickShortcuts
        } else {
            shortcuts?.isVisible = false
        }

        findPreference<Preference>("version")?.let {
            it.summary = BuildConfig.VERSION_NAME
        }

        presenter.onCreate()
    }

    override fun onLocationSettingChanged() {
        if (!PermissionManager.checkLocationPermission(requireContext())) {
            PermissionManager.requestLocationPermissions(this)
        }
        PermissionManager.restartLocationTracking(requireContext())
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionManager.validateLocationPermissions(requestCode, grantResults)) {
            PermissionManager.restartLocationTracking(requireContext())
        } else {
            // If we don't have permissions, don't let them in!
            findPreference<SwitchPreference>("location_zone")!!.isChecked = false
            findPreference<SwitchPreference>("location_background")!!.isChecked = false
        }
    }

    private fun authenticationResult(result: Int) {
        val switchLock = findPreference<SwitchPreference>("app_lock")
        switchLock?.isChecked = result == Authenticator.SUCCESS
    }
}
