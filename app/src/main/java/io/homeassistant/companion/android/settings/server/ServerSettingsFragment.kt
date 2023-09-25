package io.homeassistant.companion.android.settings.server

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.ssid.SsidFragment
import io.homeassistant.companion.android.settings.url.ExternalUrlFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ServerSettingsFragment : ServerSettingsView, PreferenceFragmentCompat() {

    companion object {
        const val TAG = "ServerSettingsFragment"

        const val EXTRA_SERVER = "server"
    }

    @Inject
    lateinit var presenter: ServerSettingsPresenter

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsResult(it)
    }

    private var serverId = -1

    private var serverDeleteDialog: AlertDialog? = null
    private var serverDeleteHandler = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        arguments?.let {
            serverId = it.getInt(EXTRA_SERVER, serverId)
        }
        presenter.init(this, serverId)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences_server, rootKey)

        val onChangeUrlValidator = Preference.OnPreferenceChangeListener { _, newValue ->
            val isValid = newValue.toString().isBlank() || newValue.toString().toHttpUrlOrNull() != null
            if (!isValid) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(commonR.string.url_invalid)
                    .setMessage(commonR.string.url_parse_error)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
            }
            isValid
        }

        if (presenter.hasMultipleServers()) {
            val activateClickListener = OnPreferenceClickListener {
                val intent = WebViewActivity.newInstance(requireContext(), null, serverId).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                requireContext().startActivity(intent)
                return@OnPreferenceClickListener true
            }
            findPreference<Preference>("activate_server")?.let {
                it.isVisible = true
                it.onPreferenceClickListener = activateClickListener
            }
            findPreference<Preference>("activate_server_hint")?.let {
                it.isVisible = true
                it.onPreferenceClickListener = activateClickListener
            }
        }

        findPreference<SwitchPreference>("app_lock")?.setOnPreferenceChangeListener { _, newValue ->
            val isValid: Boolean
            if (newValue == false) {
                isValid = true
                findPreference<SwitchPreference>("app_lock_home_bypass")?.isVisible = false
                findPreference<EditTextPreference>("session_timeout")?.isVisible = false
            } else {
                val settingsActivity = requireActivity() as SettingsActivity
                val canAuth = settingsActivity.requestAuthentication(getString(commonR.string.biometric_set_title), ::setLockAuthenticationResult)
                isValid = canAuth

                if (!canAuth) {
                    AlertDialog.Builder(requireActivity())
                        .setTitle(commonR.string.set_lock_title)
                        .setMessage(commonR.string.set_lock_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }
            }
            isValid
        }

        findPreference<SwitchPreference>("app_lock_home_bypass")?.let {
            it.isVisible = findPreference<SwitchPreference>("app_lock")?.isChecked == true && presenter.hasWifi()
        }

        findPreference<EditTextPreference>("session_timeout")?.let { pref ->
            pref.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref.isVisible = findPreference<SwitchPreference>("app_lock")?.isChecked == true
        }

        findPreference<EditTextPreference>("connection_internal")?.let {
            it.setOnBindEditTextListener { edit ->
                edit.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            it.onPreferenceChangeListener =
                onChangeUrlValidator
            it.isVisible = presenter.hasWifi()
        }

        findPreference<Preference>("connection_external")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(
                    R.id.content,
                    ExternalUrlFragment::class.java,
                    Bundle().apply { putInt(ExternalUrlFragment.EXTRA_SERVER, serverId) }
                )
                addToBackStack(getString(commonR.string.input_url))
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("connection_internal_ssids")?.let {
            it.setOnPreferenceClickListener {
                onDisplaySsidScreen()
                return@setOnPreferenceClickListener true
            }
            it.isVisible = presenter.hasWifi()
        }

        findPreference<PreferenceCategory>("security_category")?.isVisible = Build.MODEL != "Quest"

        findPreference<Preference>("websocket")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(
                        R.id.content,
                        WebsocketSettingFragment::class.java,
                        Bundle().apply { putInt(WebsocketSettingFragment.EXTRA_SERVER, serverId) }
                    )
                    addToBackStack(getString(commonR.string.websocket_setting_name))
                }
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<Preference>("delete_server")?.let {
            it.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setMessage(commonR.string.server_delete_confirm)
                    .setPositiveButton(commonR.string.delete) { dialog, _ ->
                        dialog.cancel()
                        serverDeleteHandler.postDelayed({
                            serverDeleteDialog = AlertDialog.Builder(requireContext())
                                .setMessage(commonR.string.server_delete_working)
                                .setCancelable(false)
                                .create()
                            serverDeleteDialog?.show()
                        }, 2500L)
                        lifecycleScope.launch { presenter.deleteServer() }
                    }
                    .setNegativeButton(commonR.string.cancel, null)
                    .show()
                return@setOnPreferenceClickListener true
            }
        }
    }

    override fun updateServerName(name: String) {
        activity?.title = name.ifBlank { getString(commonR.string.server_settings) }
        findPreference<EditTextPreference>("server_name")?.let {
            it.summary = name
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
                if (useCloud) {
                    getString(commonR.string.input_cloud)
                } else {
                    url
                }
        }
    }

    override fun updateSsids(ssids: List<String>) {
        findPreference<Preference>("connection_internal_ssids")?.let {
            it.summary =
                if (ssids.isEmpty()) {
                    getString(commonR.string.pref_connection_ssids_empty)
                } else {
                    ssids.joinToString()
                }
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
                    requireContext(),
                    permissionsToCheck,
                    continueYesCallback = {
                        requestLocationPermission()
                        // showSsidSettings() will be called if permission is granted
                    }
                )
            } else {
                showSsidSettings()
            }
        } else {
            if (presenter.isSsidUsed()) {
                DisabledLocationHandler.showLocationDisabledWarnDialog(
                    requireActivity(),
                    arrayOf(
                        getString(commonR.string.pref_connection_wifi)
                    ),
                    showAsNotification = false,
                    withDisableOption = true
                ) {
                    presenter.clearSsids()
                }
            } else {
                DisabledLocationHandler.showLocationDisabledWarnDialog(
                    requireActivity(),
                    arrayOf(
                        getString(commonR.string.pref_connection_wifi)
                    )
                )
            }
        }
    }

    private fun showSsidSettings() {
        parentFragmentManager.commit {
            replace(
                R.id.content,
                SsidFragment::class.java,
                Bundle().apply { putInt(SsidFragment.EXTRA_SERVER, serverId) }
            )
            addToBackStack(getString(commonR.string.manage_ssids))
        }
    }

    private fun setLockAuthenticationResult(result: Int): Boolean {
        val success = result == Authenticator.SUCCESS
        val switchLock = findPreference<SwitchPreference>("app_lock")
        switchLock?.isChecked = success

        // Prevent requesting authentication after just enabling the app lock
        presenter.setAppActive(true)

        findPreference<SwitchPreference>("app_lock_home_bypass")?.isVisible = success && presenter.hasWifi()
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

    override fun onRemovedServer(success: Boolean, hasAnyRemaining: Boolean) {
        serverDeleteHandler.removeCallbacksAndMessages(null)
        serverDeleteDialog?.cancel()
        if (success && context != null) {
            if (hasAnyRemaining) { // Return to the main settings screen
                parentFragmentManager.popBackStack()
            } else { // Relaunch app
                startActivity(Intent(requireContext(), LaunchActivity::class.java))
                requireActivity().finishAffinity()
            }
        }
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

        presenter.updateServerName()
        presenter.updateUrlStatus()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    fun getServerId(): Int = serverId
}
