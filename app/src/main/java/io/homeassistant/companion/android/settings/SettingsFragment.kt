package io.homeassistant.companion.android.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
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
import io.homeassistant.companion.android.settings.language.LanguagesProvider
import io.homeassistant.companion.android.settings.log.LogFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.settings.ssid.SsidPreference
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsSettingsFragment
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.util.LocationPermissionInfoHandler
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    companion object {
        private const val SSID_DIALOG_TAG = "${BuildConfig.APPLICATION_ID}.SSID_DIALOG_TAG"
        private const val LOCATION_REQUEST_CODE = 0
        private const val BACKGROUND_LOCATION_REQUEST_CODE = 1
        fun newInstance() = SettingsFragment()
    }

    @Inject
    lateinit var presenter: SettingsPresenter

    @Inject
    lateinit var langProvider: LanguagesProvider
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
                    authenticator.authenticate(getString(R.string.biometric_set_title))
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
            val pm: PackageManager = requireContext().packageManager
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC))
                it.isVisible = presenter.nfcEnabled()
            else
                it.isVisible = false
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(NfcSetupActivity.newInstance(requireActivity()))
                true
            }
        }

        removeSystemFromThemesIfNeeded()

        updateBackgroundAccessPref()

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

        findPreference<Preference>("manage_widgets")?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, ManageWidgetsSettingsFragment.newInstance())
                .addToBackStack(getString(R.string.widgets))
                .commit()
            return@setOnPreferenceClickListener true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            findPreference<PreferenceCategory>("shortcuts")?.let {
                it.isVisible = true
            }
            findPreference<Preference>("manage_shortcuts")?.setOnPreferenceClickListener {
                parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.content, ManageShortcutsSettingsFragment.newInstance())
                        .addToBackStack(getString(R.string.shortcuts))
                        .commit()
                return@setOnPreferenceClickListener true
            }
        }

        if (BuildConfig.FLAVOR == "full") {
            findPreference<PreferenceCategory>("notifications")?.let {
                it.isVisible = true
            }
            findPreference<Preference>("notification_history")?.let {
                it.isVisible = true
                it.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.content, NotificationHistoryFragment.newInstance())
                        .addToBackStack(getString(R.string.notifications))
                        .commit()
                    return@setOnPreferenceClickListener true
                }
            }

            findPreference<Preference>("notification_rate_limit")?.let {
                val rateLimits = presenter.getNotificationRateLimits()

                if (rateLimits != null)
                    it.isVisible = true
                it.summary = "\nSuccessful: ${rateLimits?.successful}       Errors: ${rateLimits?.errors}" +
                        "\n\nRemaining/Maximum: ${rateLimits?.remaining}/${rateLimits?.maximum}" +
                        "\n\nResets at: ${rateLimits?.resetsAt}"
            }
            findPreference<SwitchPreference>("crash_reporting")?.let {
                it.isVisible = true
                it.setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean

                    true
                }
            }
        }

        findPreference<Preference>("changelog")?.let {
            val link = if (BuildConfig.VERSION_NAME.startsWith("LOCAL"))
                "https://github.com/home-assistant/android/releases"
            else "https://github.com/home-assistant/android/releases/tag/${BuildConfig.VERSION_NAME.replace("-full", "").replace("-minimal", "")}"
            it.summary = link
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        }

        findPreference<Preference>("version")?.let {
            it.isCopyingEnabled = true
            it.summary = BuildConfig.VERSION_NAME
        }

        findPreference<ListPreference>("languages")?.let {
            val languages = langProvider.getSupportedLanguages(requireContext())
            it.entries = languages.keys.toTypedArray()
            it.entryValues = languages.values.toTypedArray()
        }

        findPreference<Preference>("privacy")?.let {
            it.summary = "https://www.home-assistant.io/privacy/"
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.summary.toString()))
        }

        findPreference<Preference>("show_share_logs")?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, LogFragment.newInstance())
                .addToBackStack(getString(R.string.log))
                .commit()
            return@setOnPreferenceClickListener true
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

    override fun onLangSettingsChanged() {
        requireActivity().recreate()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SsidPreference) {
            val permissionsToCheck: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            if (DisabledLocationHandler.isLocationEnabled(requireContext())) {
                var permissionsToRequest: Array<String>? = null
                if (!permissionsToCheck.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // For Android 11 we MUST NOT request Background Location permission with fine or coarse permissions
                    // as for Android 11 the background location request needs to be done separately
                    // See here: https://developer.android.com/about/versions/11/privacy/location#request-background-location-separately
                    permissionsToRequest = permissionsToCheck.toList().minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray()
                }

                val hasPermission = checkPermission(permissionsToCheck)
                if (permissionsToCheck.isNotEmpty() && !hasPermission) {
                    LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(requireContext(), permissionsToCheck, continueYesCallback = {
                        checkAndRequestPermissions(permissionsToCheck, LOCATION_REQUEST_CODE, permissionsToRequest, true)
                        // openSsidDialog() will be called in onRequestPermissionsResult if permission is granted
                    })
                } else openSsidDialog()
            } else {
                if (presenter.isSsidUsed()) {
                    DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), arrayOf(getString(R.string.pref_connection_wifi)), showAsNotification = false, withDisableOption = true) {
                        presenter.clearSsids()
                        preference.setSsids(emptySet())
                    }
                } else {
                    DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), arrayOf(getString(R.string.pref_connection_wifi)))
                }
            }
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

    private fun updateBackgroundAccessPref() {
        findPreference<Preference>("background")?.let {
            if (isIgnoringBatteryOptimizations()) {
                it.setSummary(R.string.background_access_enabled)
                it.setOnPreferenceClickListener {
                    true
                }
            } else {
                it.setSummary(R.string.background_access_disabled)
                it.setOnPreferenceClickListener {
                    requestBackgroundAccess()
                    true
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        val intent: Intent
        if (!isIgnoringBatteryOptimizations()) {
            intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity?.packageName}")
            )
            startActivityForResult(intent, 0)
        }
    }

    private fun checkAndRequestPermissions(permissions: Array<String>, requestCode: Int, requestPermissions: Array<String>? = null, forceRequest: Boolean = false): Boolean {
        val permissionsNeeded = mutableListOf<String>()
        for (permission in permissions) {
            if (forceRequest || ContextCompat.checkSelfPermission(requireContext(), permission) === PackageManager.PERMISSION_DENIED) {
                if (requestPermissions.isNullOrEmpty() || requestPermissions.contains(permission)) {
                    permissionsNeeded.add(permission)
                }
            }
        }
        return if (permissionsNeeded.isNotEmpty()) {
            requestPermissions(permissionsNeeded.toTypedArray(), requestCode)
            false
        } else true
    }

    fun checkPermission(permissions: Array<String>?): Boolean {
        if (!permissions.isNullOrEmpty()) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) === PackageManager.PERMISSION_DENIED) {
                    return false
                }
            }
        }
        return true
    }

    private fun openSsidDialog() {
        // check if dialog is already showing
        val fm = parentFragmentManager
        if (fm.findFragmentByTag(SSID_DIALOG_TAG) != null) {
            return
        }

        val ssidDialog = SsidDialogFragment.newInstance("connection_internal_ssids")
        ssidDialog.setTargetFragment(this, 0)
        ssidDialog.show(fm, SSID_DIALOG_TAG)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
                context?.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(requireActivity().packageName)
                ?: false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateBackgroundAccessPref()

        val isGreaterR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        if (requestCode == LOCATION_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (isGreaterR) {
                    // For Android 11 we MUST NOT request Background Location permission with fine or coarse permissions
                    // as for Android 11 the background location request needs to be done separately
                    // See here: https://developer.android.com/about/versions/11/privacy/location#request-background-location-separately
                    // The separate request of background location is done here
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), BACKGROUND_LOCATION_REQUEST_CODE)
                }
            }
        }
        if ((requestCode == LOCATION_REQUEST_CODE && !isGreaterR || requestCode == BACKGROUND_LOCATION_REQUEST_CODE && isGreaterR) && grantResults.isNotEmpty()) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                openSsidDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.app_name)
    }
}
