package io.homeassistant.companion.android.settings

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.settings.controls.ManageControlsSettingsFragment
import io.homeassistant.companion.android.settings.language.LanguagesProvider
import io.homeassistant.companion.android.settings.log.LogFragment
import io.homeassistant.companion.android.settings.notification.NotificationChannelFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorSettingsFragment
import io.homeassistant.companion.android.settings.sensor.SensorUpdateFrequencyFragment
import io.homeassistant.companion.android.settings.server.ServerSettingsFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.wear.SettingsWearActivity
import io.homeassistant.companion.android.settings.wear.SettingsWearDetection
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsSettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.homeassistant.companion.android.common.R as commonR

class SettingsFragment constructor(
    val presenter: SettingsPresenter,
    val langProvider: LanguagesProvider
) : PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private val requestBackgroundAccessResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateBackgroundAccessPref()
    }

    private val requestNotificationPermissionResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateNotificationChannelPrefs()
    }

    private val requestOnboardingResult = registerForActivityResult(OnboardApp(), this::onOnboardingComplete)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences, rootKey)

        // This should enumerate over all servers in the future
        val serverPreference = Preference(requireContext())
        presenter.getServerRegistrationName()?.let {
            serverPreference.title = it
            serverPreference.summary = presenter.getServerName()
        } ?: run {
            serverPreference.title = presenter.getServerName()
        }
        serverPreference.order = 1
        try {
            serverPreference.icon = AppCompatResources.getDrawable(requireContext(), commonR.drawable.ic_stat_ic_notification_blue)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to set the server icon", e)
        }
        serverPreference.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, ServerSettingsFragment::class.java, null)
                addToBackStack(getString(commonR.string.server_settings))
            }
            return@setOnPreferenceClickListener true
        }
        findPreference<PreferenceCategory>("servers_devices_category")?.addPreference(serverPreference)

        findPreference<Preference>("nfc_tags")?.let {
            val pm: PackageManager = requireContext().packageManager
            it.isVisible = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(NfcSetupActivity.newInstance(requireActivity()))
                true
            }
        }

        removeSystemFromThemesIfNeeded()

        updateBackgroundAccessPref()

        findPreference<Preference>("server_add")?.let {
            it.setOnPreferenceClickListener {
                requestOnboardingResult.launch(OnboardApp.Input())
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<Preference>("sensors")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, SensorSettingsFragment::class.java, null)
                addToBackStack(getString(commonR.string.sensors))
            }
            return@setOnPreferenceClickListener true
        }
        findPreference<Preference>("sensor_update_frequency")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(R.id.content, SensorUpdateFrequencyFragment::class.java, null)
                    addToBackStack(getString(commonR.string.sensor_update_frequency))
                }
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<PreferenceCategory>("widgets")?.isVisible = Build.MODEL != "Quest"
        findPreference<Preference>("manage_widgets")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, ManageWidgetsSettingsFragment::class.java, null)
                addToBackStack(getString(commonR.string.widgets))
            }
            return@setOnPreferenceClickListener true
        }

        if (Build.MODEL != "Quest") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                findPreference<PreferenceCategory>("shortcuts")?.let {
                    it.isVisible = true
                }
                findPreference<Preference>("manage_shortcuts")?.setOnPreferenceClickListener {
                    parentFragmentManager.commit {
                        replace(R.id.content, ManageShortcutsSettingsFragment::class.java, null)
                        addToBackStack(getString(commonR.string.shortcuts))
                    }
                    return@setOnPreferenceClickListener true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                findPreference<PreferenceCategory>("quick_settings")?.let {
                    it.isVisible = true
                }
                findPreference<Preference>("manage_tiles")?.setOnPreferenceClickListener {
                    parentFragmentManager.commit {
                        replace(R.id.content, ManageTilesFragment::class.java, null)
                        addToBackStack(getString(commonR.string.tiles))
                    }
                    return@setOnPreferenceClickListener true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                findPreference<PreferenceCategory>("device_controls")?.let {
                    it.isVisible = true
                }
                findPreference<Preference>("manage_device_controls")?.setOnPreferenceClickListener {
                    parentFragmentManager.commit {
                        replace(R.id.content, ManageControlsSettingsFragment::class.java, null)
                        addToBackStack(getString(commonR.string.controls_setting_title))
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }

        findPreference<PreferenceCategory>("notifications")?.let {
            it.isVisible = true
        }

        updateNotificationChannelPrefs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            findPreference<Preference>("notification_permission")?.let {
                it.setOnPreferenceClickListener {
                    openNotificationSettings()
                    return@setOnPreferenceClickListener true
                }
            }

            findPreference<Preference>("notification_channels")?.let { pref ->
                pref.setOnPreferenceClickListener {
                    parentFragmentManager.commit {
                        replace(R.id.content, NotificationChannelFragment::class.java, null)
                        addToBackStack(getString(commonR.string.notification_channels))
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }

        findPreference<Preference>("notification_history")?.let {
            it.isVisible = true
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(R.id.content, NotificationHistoryFragment::class.java, null)
                    addToBackStack(getString(commonR.string.notifications))
                }
                return@setOnPreferenceClickListener true
            }
        }

        if (BuildConfig.FLAVOR == "full") {
            findPreference<Preference>("notification_rate_limit")?.let {

                lifecycleScope.launch(Dispatchers.Main) {
                    // Runs in IO Dispatcher
                    val rateLimits = presenter.getNotificationRateLimits()

                    if (rateLimits != null) {
                        var formattedDate = rateLimits.resetsAt
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val utcDateTime = Instant.parse(rateLimits.resetsAt)
                                formattedDate = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(utcDateTime.atZone(ZoneId.systemDefault()))
                            } catch (e: Exception) {
                                Log.d(TAG, "Cannot parse notification rate limit date \"${rateLimits.resetsAt}\"", e)
                            }
                        }
                        it.isVisible = true
                        it.summary = "\n${getString(commonR.string.successful)}: ${rateLimits.successful}       ${getString(commonR.string.errors)}: ${rateLimits.errors}" +
                            "\n\n${getString(commonR.string.remaining)}/${getString(commonR.string.maximum)}: ${rateLimits.remaining}/${rateLimits.maximum}" +
                            "\n\n${getString(commonR.string.resets_at)}: $formattedDate"
                    }
                }
            }
        }
        findPreference<SwitchPreference>("crash_reporting")?.let {
            it.isVisible = BuildConfig.FLAVOR == "full"
            it.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                true
            }
        }

        lifecycleScope.launch {
            findPreference<Preference>("wear_settings")?.let {
                it.isVisible = SettingsWearDetection.hasAnyNodes(requireContext())
                it.setOnPreferenceClickListener {
                    startActivity(SettingsWearActivity.newInstance(requireContext()))
                    return@setOnPreferenceClickListener true
                }
            }
        }

        findPreference<Preference>("changelog_github")?.let {
            val link = if (BuildConfig.VERSION_NAME.startsWith("LOCAL"))
                "https://github.com/home-assistant/android/releases"
            else "https://github.com/home-assistant/android/releases/tag/${BuildConfig.VERSION_NAME.replace("-full", "").replace("-minimal", "")}"
            it.summary = link
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        }

        findPreference<Preference>("changelog_prompt")?.setOnPreferenceClickListener {
            presenter.showChangeLog(requireContext())
            true
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
            parentFragmentManager.commit {
                replace(R.id.content, LogFragment::class.java, null)
                addToBackStack(getString(commonR.string.log))
            }
            return@setOnPreferenceClickListener true
        }
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
                it.setSummary(commonR.string.background_access_enabled)
                it.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_check)
                it.setOnPreferenceClickListener {
                    true
                }
            } else {
                it.setSummary(commonR.string.background_access_disabled)
                it.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_close)
                it.setOnPreferenceClickListener {
                    requestBackgroundAccess()
                    true
                }
            }
        }
    }

    private fun updateNotificationChannelPrefs() {
        val notificationsEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

        findPreference<Preference>("notification_permission")?.let {
            it.isVisible = !notificationsEnabled
        }
        findPreference<Preference>("notification_channels")?.let {
            val uiManager = requireContext().getSystemService<UiModeManager>()
            it.isVisible =
                notificationsEnabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                uiManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION
        }
    }

    private fun onOnboardingComplete(result: OnboardApp.Output?) {
        lifecycleScope.launch {
            presenter.addServer(result)
            presenter.updateExternalUrlStatus()
            presenter.updateInternalUrlStatus()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        if (!isIgnoringBatteryOptimizations()) {
            requestBackgroundAccessResult.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${activity?.packageName}")
                )
            )
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestNotificationPermissionResult.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
            )
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
            context?.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(requireActivity().packageName)
                ?: false
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.companion_app)
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
