package io.homeassistant.companion.android.settings

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.settings.controls.ManageControlsSettingsFragment
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsFragment
import io.homeassistant.companion.android.settings.language.LanguagesProvider
import io.homeassistant.companion.android.settings.notification.NotificationChannelFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorSettingsFragment
import io.homeassistant.companion.android.settings.sensor.SensorUpdateFrequencyFragment
import io.homeassistant.companion.android.settings.server.ServerSettingsFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.vehicle.ManageAndroidAutoSettingsFragment
import io.homeassistant.companion.android.settings.wear.SettingsWearActivity
import io.homeassistant.companion.android.settings.wear.SettingsWearDetection
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsSettingsFragment
import io.homeassistant.companion.android.webview.WebViewActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsFragment(
    private val presenter: SettingsPresenter,
    private val langProvider: LanguagesProvider
) : SettingsView, PreferenceFragmentCompat() {

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

    private var serverAuth: Int? = null
    private val serverMutex = Mutex()

    private var snackbar: Snackbar? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        presenter.init(this)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences, rootKey)

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                presenter.getServersFlow().collect {
                    updateServers(it)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                presenter.getSuggestionFlow().collect { suggestion ->
                    findPreference<SettingsSuggestionPreference>("settings_suggestion")?.let {
                        if (suggestion != null) {
                            it.setTitle(suggestion.title)
                            it.setSummary(suggestion.summary)
                            it.setIcon(suggestion.icon)
                            it.setOnPreferenceClickListener {
                                when (suggestion.id) {
                                    SettingsPresenter.SUGGESTION_ASSISTANT_APP -> updateAssistantApp()
                                    SettingsPresenter.SUGGESTION_NOTIFICATION_PERMISSION -> openNotificationSettings()
                                }
                                return@setOnPreferenceClickListener true
                            }
                            it.setOnPreferenceCancelListener { presenter.cancelSuggestion(requireContext(), suggestion.id) }
                        }
                        it.isVisible = suggestion != null
                    }
                }
            }
        }

        findPreference<Preference>("server_add")?.let {
            it.setOnPreferenceClickListener {
                requestOnboardingResult.launch(
                    OnboardApp.Input(
                        // Empty url skips the 'Welcome' screen
                        url = "",
                        discoveryOptions = OnboardApp.DiscoveryOptions.HIDE_EXISTING
                    )
                )
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

        findPreference<ListPreference>("page_zoom")?.let {
            // The list of percentages for iOS/Android should match
            // https://github.com/home-assistant/iOS/blob/ff66bbf2e3f9add0abb0b492499b81e824db36ed/Sources/Shared/Settings/SettingsStore.swift#L108
            val percentages = listOf(50, 75, 85, 100, 115, 125, 150, 175, 200)
            it.entries = percentages.map { pct ->
                getString(if (pct == 100) commonR.string.page_zoom_default else commonR.string.page_zoom_pct, pct)
            }.toTypedArray()
            it.entryValues = percentages.map { pct -> pct.toString() }.toTypedArray()
        }

        findPreference<PreferenceCategory>("widgets")?.isVisible = Build.MODEL != "Quest"
        findPreference<Preference>("manage_widgets")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, ManageWidgetsSettingsFragment::class.java, null)
                addToBackStack(getString(commonR.string.widgets))
            }
            return@setOnPreferenceClickListener true
        }

        val isAutomotive =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

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

            if (!isAutomotive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            val link = if (BuildConfig.VERSION_NAME.startsWith("LOCAL")) {
                "https://github.com/home-assistant/android/releases"
            } else {
                "https://github.com/home-assistant/android/releases/tag/${BuildConfig.VERSION_NAME.replace("-full", "").replace("-minimal", "")}"
            }
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

        findPreference<Preference>("developer")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, DeveloperSettingsFragment::class.java, null)
                addToBackStack(getString(commonR.string.troubleshooting))
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<PreferenceCategory>("android_auto")?.let {
            it.isVisible =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (BuildConfig.FLAVOR == "full" || isAutomotive)
            if (isAutomotive) {
                it.title = getString(commonR.string.android_automotive)
            }
        }

        findPreference<Preference>("auto_favorites")?.let { pref ->
            if (isAutomotive) {
                pref.title = getString(commonR.string.android_automotive_favorites)
            }
            pref.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(R.id.content, ManageAndroidAutoSettingsFragment::class.java, null)
                    addToBackStack(getString(commonR.string.basic_sensor_name_android_auto))
                }
                return@setOnPreferenceClickListener true
            }
        }
    }

    private fun removeSystemFromThemesIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val pref = findPreference<ListPreference>("themes")
            if (pref != null) {
                val systemIndex = pref.findIndexOfValue("system")
                if (systemIndex > 0) {
                    val entries = pref.entries?.toMutableList()
                    entries?.removeAt(systemIndex)
                    val entryValues = pref.entryValues?.toMutableList()
                    entryValues?.removeAt(systemIndex)
                    if (entries != null && entryValues != null) {
                        pref.entries = entries.toTypedArray()
                        pref.entryValues = entryValues.toTypedArray()
                    }
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun updateAssistantApp() {
        // On Android Q+, this is a workaround as Android doesn't allow requesting the assistant role
        try {
            val openIntent = Intent(Intent.ACTION_MAIN)
            openIntent.component = ComponentName("com.android.settings", "com.android.settings.Settings\$ManageAssistActivity")
            openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            // The exact activity/package doesn't exist on this device, use the official intent
            // which sends the user to the 'Default apps' screen (one more tap required to change)
            startActivity(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
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

    private suspend fun updateServers(servers: List<Server>) = serverMutex.withLock {
        val category = findPreference<PreferenceCategory>("servers_devices_category")

        val numPreferences = category?.preferenceCount ?: 0
        val serverPreferences = mutableListOf<Preference>()
        val serverKeys = servers.mapIndexed { index, server ->
            "server_${index}_${server.id}_${server.friendlyName}_${server.deviceName}"
        }
        for (i in 0 until numPreferences) {
            category?.getPreference(i)?.let {
                if (it.key != "server_add" && it.key != "wear_settings") serverPreferences += it
            }
        }
        serverPreferences.forEach {
            if (it.key !in serverKeys) category?.removePreference(it)
        }

        servers.forEachIndexed { index, server ->
            if (serverKeys[index] in serverPreferences.map { it.key }) return@forEachIndexed // Already exists!
            val serverPreference = Preference(requireContext())
            serverPreference.title = server.friendlyName
            serverPreference.summary = server.deviceName
            serverPreference.key = serverKeys[index]
            serverPreference.order = index
            try {
                serverPreference.icon = AppCompatResources.getDrawable(requireContext(), commonR.drawable.ic_stat_ic_notification_blue)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to set the server icon", e)
            }
            serverPreference.setOnPreferenceClickListener {
                serverAuth = server.id
                val settingsActivity = requireActivity() as SettingsActivity
                val needsAuth = settingsActivity.isAppLocked(server.id)
                if (!needsAuth) {
                    onServerLockResult(Authenticator.SUCCESS)
                } else {
                    val canAuth = settingsActivity.requestAuthentication(getString(commonR.string.biometric_set_title), ::onServerLockResult)
                    if (!canAuth) {
                        onServerLockResult(Authenticator.SUCCESS)
                    }
                }
                return@setOnPreferenceClickListener true
            }
            category?.addPreference(serverPreference)
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

    private fun onServerLockResult(result: Int): Boolean {
        if (result == Authenticator.SUCCESS && serverAuth != null) {
            (activity as? SettingsActivity)?.setAppActive(serverAuth, true)
            parentFragmentManager.commit {
                replace(
                    R.id.content,
                    ServerSettingsFragment::class.java,
                    Bundle().apply { putInt(ServerSettingsFragment.EXTRA_SERVER, serverAuth!!) },
                    ServerSettingsFragment.TAG
                )
                addToBackStack(getString(commonR.string.server_settings))
            }
        }
        return true
    }

    private fun onOnboardingComplete(result: OnboardApp.Output?) {
        lifecycleScope.launch {
            presenter.addServer(result)
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

    override fun onAddServerResult(success: Boolean, serverId: Int?) {
        view?.let {
            snackbar = Snackbar.make(
                it,
                if (success) commonR.string.server_add_success else commonR.string.server_add_failed,
                5_000
            ).apply {
                if (success && serverId != null) {
                    setAction(commonR.string.activate) {
                        val intent = WebViewActivity.newInstance(requireContext(), null, serverId).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        requireContext().startActivity(intent)
                    }
                }
                show()
            }
        }
    }

    override fun getPackageManager(): PackageManager? = context?.packageManager

    override fun onPause() {
        super.onPause()
        snackbar?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.companion_app)
        context?.let { presenter.updateSuggestions(it) }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
