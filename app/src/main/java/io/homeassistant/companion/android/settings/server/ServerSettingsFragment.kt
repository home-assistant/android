package io.homeassistant.companion.android.settings.server

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.ssid.SsidFragment
import io.homeassistant.companion.android.settings.url.ExternalUrlFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ServerSettingsFragment : ServerSettingsView, PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "ServerSettingsFragment"
    }

    @Inject
    lateinit var presenter: ServerSettingsPresenter

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsResult(it)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        presenter.init(this)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences_server, rootKey)

        val onChangeUrlValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            val isValid = newValue.toString().isBlank() || newValue.toString().toHttpUrlOrNull() != null
            if (!isValid) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(io.homeassistant.companion.android.common.R.string.url_invalid)
                    .setMessage(io.homeassistant.companion.android.common.R.string.url_parse_error)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
            isValid
        }

        findPreference<SwitchPreference>("app_lock")?.setOnPreferenceChangeListener { _, newValue ->
            val isValid: Boolean
            if (newValue == false) {
                isValid = true
                findPreference<SwitchPreference>("app_lock_home_bypass")?.isVisible = false
                findPreference<EditTextPreference>("session_timeout")?.isVisible = false
            } else {
                val settingsActivity = requireActivity() as SettingsActivity
                val canAuth = settingsActivity.requestAuthentication(getString(io.homeassistant.companion.android.common.R.string.biometric_set_title), ::setLockAuthenticationResult)
                isValid = canAuth

                if (!canAuth) {
                    AlertDialog.Builder(requireActivity())
                        .setTitle(io.homeassistant.companion.android.common.R.string.set_lock_title)
                        .setMessage(io.homeassistant.companion.android.common.R.string.set_lock_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
            }
            isValid
        }

        findPreference<SwitchPreference>("app_lock_home_bypass")?.let {
            it.isVisible = findPreference<SwitchPreference>("app_lock")?.isChecked == true
        }

        findPreference<EditTextPreference>("session_timeout")?.let { pref ->
            pref.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref.isVisible = findPreference<SwitchPreference>("app_lock")?.isChecked == true
        }

        findPreference<EditTextPreference>("connection_internal")?.let {
            it.onPreferenceChangeListener =
                onChangeUrlValidator
        }

        findPreference<Preference>("connection_external")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, ExternalUrlFragment::class.java, null)
                addToBackStack(getString(io.homeassistant.companion.android.common.R.string.input_url))
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("connection_internal_ssids")?.let {
            it.setOnPreferenceClickListener {
                onDisplaySsidScreen()
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<PreferenceCategory>("security_category")?.isVisible = Build.MODEL != "Quest"

        findPreference<Preference>("websocket")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(R.id.content, WebsocketSettingFragment::class.java, null)
                    addToBackStack(getString(io.homeassistant.companion.android.common.R.string.notifications))
                }
                return@setOnPreferenceClickListener true
            }
        }
    }

    override fun enableInternalConnection(isEnabled: Boolean) {
        val iconTint = if (isEnabled) ContextCompat.getColor(requireContext(), commonR.color.colorAccent) else Color.DKGRAY
        val doEnable = isEnabled && hasLocationPermission()

        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = doEnable
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_computer)
                unwrappedDrawable?.setTint(iconTint)
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Log.e(TAG, "Unable to set the icon tint", e)
            }
        }

        findPreference<SwitchPreference>("app_lock_home_bypass")?.let {
            it.isEnabled = doEnable
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_wifi)
                unwrappedDrawable?.setTint(iconTint)
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Log.e(TAG, "Unable to set the icon tint", e)
            }
        }
    }

    override fun updateExternalUrl(url: String, useCloud: Boolean) {
        findPreference<Preference>("connection_external")?.let {
            it.summary =
                if (useCloud) getString(io.homeassistant.companion.android.common.R.string.input_cloud)
                else url
        }
    }

    override fun updateSsids(ssids: Set<String>) {
        findPreference<Preference>("connection_internal_ssids")?.let {
            it.summary =
                if (ssids.isEmpty()) getString(io.homeassistant.companion.android.common.R.string.pref_connection_ssids_empty)
                else ssids.joinToString()
        }
    }

    private fun onDisplaySsidScreen() {
        val permissionsToCheck: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (DisabledLocationHandler.isLocationEnabled(requireContext())) {
            if (!checkPermission(permissionsToCheck)) {
                LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(
                    requireContext(), permissionsToCheck,
                    continueYesCallback = {
                        requestLocationPermission()
                        // showSsidSettings() will be called if permission is granted
                    }
                )
            } else showSsidSettings()
        } else {
            if (presenter.isSsidUsed()) {
                DisabledLocationHandler.showLocationDisabledWarnDialog(
                    requireActivity(),
                    arrayOf(
                        getString(
                            io.homeassistant.companion.android.common.R.string.pref_connection_wifi
                        )
                    ),
                    showAsNotification = false, withDisableOption = true
                ) {
                    presenter.clearSsids()
                }
            } else {
                DisabledLocationHandler.showLocationDisabledWarnDialog(
                    requireActivity(),
                    arrayOf(
                        getString(
                            io.homeassistant.companion.android.common.R.string.pref_connection_wifi
                        )
                    )
                )
            }
        }
    }

    private fun showSsidSettings() {
        parentFragmentManager.commit {
            replace(R.id.content, SsidFragment::class.java, null)
            addToBackStack(getString(io.homeassistant.companion.android.common.R.string.manage_ssids))
        }
    }

    private fun setLockAuthenticationResult(result: Int): Boolean {
        val success = result == Authenticator.SUCCESS
        val switchLock = findPreference<SwitchPreference>("app_lock")
        switchLock?.isChecked = success

        // Prevent requesting authentication after just enabling the app lock
        presenter.setAppActive()

        findPreference<SwitchPreference>("app_lock_home_bypass")?.isVisible = success
        findPreference<EditTextPreference>("session_timeout")?.isVisible = success
        return (result == Authenticator.SUCCESS || result == Authenticator.CANCELED)
    }

    private fun hasLocationPermission(): Boolean {
        val permissionsToCheck: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return checkPermission(permissionsToCheck)
    }

    private fun requestLocationPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) // Background location will be requested later
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionsRequest.launch(permissions)
    }

    private fun checkPermission(permissions: Array<String>?): Boolean {
        if (!permissions.isNullOrEmpty()) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_DENIED) {
                    return false
                }
            }
        }
        return true
    }

    private fun onPermissionsResult(results: Map<String, Boolean>) {
        if (results.keys.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            // For Android 11+ we MUST NOT request Background Location permission with fine or coarse
            // permissions as for Android 11 the background location request needs to be done separately
            // See here: https://developer.android.com/about/versions/11/privacy/location#request-background-location-separately
            // The separate request of background location is done here
            permissionsRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return
        }

        if (results.entries.all { it.value }) {
            showSsidSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.server_settings)

        presenter.updateUrlStatus()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
