package io.homeassistant.companion.android.settings.server

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.USE_NEW_LAUNCHER
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.ConnectionSecurityLevelFragment
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.ssid.SsidFragment
import io.homeassistant.companion.android.settings.url.ExternalUrlFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import io.homeassistant.companion.android.util.QuestUtil
import io.homeassistant.companion.android.util.applyBottomSafeDrawingInsets
import io.homeassistant.companion.android.webview.WebViewActivity
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

private const val BASE_INVITE_URL = "https://my.home-assistant.io/invite/#"

@AndroidEntryPoint
class ServerSettingsFragment :
    PreferenceFragmentCompat(),
    ServerSettingsView {

    companion object {
        const val TAG = "ServerSettingsFragment"

        const val EXTRA_SERVER = "server"
    }

    @Inject
    lateinit var presenter: ServerSettingsPresenter

    private var serverId = -1

    private var serverDeleteDialog: AlertDialog? = null
    private var serverDeleteHandler = Handler(Looper.getMainLooper())

    @Deprecated("Deprecated in Java")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        arguments?.let {
            serverId = it.getInt(EXTRA_SERVER, serverId)
        }
        presenter.init(this, serverId)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences_server, rootKey)

        val onChangeUrlValidator = OnPreferenceChangeListener { _, newValue ->
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
                val canAuth = settingsActivity.requestAuthentication(
                    getString(commonR.string.biometric_set_title),
                    ::setLockAuthenticationResult,
                )
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
                    Bundle().apply { putInt(ExternalUrlFragment.EXTRA_SERVER, serverId) },
                )
                addToBackStack(getString(commonR.string.input_url))
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("connection_internal_ssids")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(
                        R.id.content,
                        SsidFragment::class.java,
                        Bundle().apply { putInt(SsidFragment.EXTRA_SERVER, serverId) },
                    )
                    addToBackStack(getString(commonR.string.pref_connection_homenetwork))
                }
                return@setOnPreferenceClickListener true
            }
            it.isVisible = presenter.hasWifi()
        }

        findPreference<Preference>("connection_security_level")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(
                        R.id.content_full_screen,
                        ConnectionSecurityLevelFragment::class.java,
                        Bundle().apply { putInt(ConnectionSecurityLevelFragment.EXTRA_SERVER, serverId) },
                    )
                    addToBackStack(null)
                }
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<PreferenceCategory>("security_category")?.isVisible = !QuestUtil.isQuest

        findPreference<Preference>("websocket")?.let {
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(
                        R.id.content,
                        WebsocketSettingFragment::class.java,
                        Bundle().apply { putInt(WebsocketSettingFragment.EXTRA_SERVER, serverId) },
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
                        serverDeleteHandler.postDelayed(
                            {
                                serverDeleteDialog = AlertDialog.Builder(requireContext())
                                    .setMessage(commonR.string.server_delete_working)
                                    .setCancelable(false)
                                    .create()
                                serverDeleteDialog?.show()
                            },
                            2500L,
                        )
                        lifecycleScope.launch { presenter.deleteServer() }
                    }
                    .setNegativeButton(commonR.string.cancel, null)
                    .show()
                return@setOnPreferenceClickListener true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyBottomSafeDrawingInsets()

        setFragmentResultListener(ConnectionSecurityLevelFragment.RESULT_KEY) { _, _ ->
            updateSecurityLevelSummary()
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_fragment_share, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    lifecycleScope.launch {
                        menu.findItem(R.id.share_server).isVisible = presenter.serverURL() != null
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                    R.id.share_server -> {
                        menuItem.isChecked = true
                        lifecycleScope.launch {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_SUBJECT, getString(commonR.string.join_our_server))
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "$BASE_INVITE_URL${URLEncoder.encode(
                                        presenter.serverURL(),
                                        Charsets.UTF_8.toString(),
                                    )}",
                                )
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(sendIntent, null))
                        }
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun updateServerName(name: String) {
        activity?.title = name.ifBlank { getString(commonR.string.server_settings) }
        findPreference<EditTextPreference>("server_name")?.let {
            it.summary = name
        }
    }

    override fun enableInternalConnection(isEnabled: Boolean) {
        val iconTint = if (isEnabled) {
            ContextCompat.getColor(
                requireContext(),
                commonR.color.colorAccent,
            )
        } else {
            Color.DKGRAY
        }

        findPreference<EditTextPreference>("connection_internal")?.let {
            it.isEnabled = isEnabled
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_computer)
                unwrappedDrawable?.setTint(iconTint)
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Timber.e(e, "Unable to set the icon tint")
            }
        }

        findPreference<SwitchPreference>("app_lock_home_bypass")?.let {
            it.isEnabled = isEnabled
            try {
                val unwrappedDrawable =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_wifi)
                unwrappedDrawable?.setTint(iconTint)
                it.icon = unwrappedDrawable
            } catch (e: Exception) {
                Timber.e(e, "Unable to set the icon tint")
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

    override fun updateHomeNetwork(ssids: List<String>, ethernet: Boolean?, vpn: Boolean?) {
        findPreference<Preference>("connection_internal_ssids")?.let {
            it.summary =
                if (ssids.isEmpty() && ethernet != true && vpn != true) {
                    getString(commonR.string.not_set)
                } else {
                    val options = ssids.toMutableList()
                    if (ethernet == true) options += getString(commonR.string.manage_ssids_ethernet)
                    if (vpn == true) options += getString(commonR.string.manage_ssids_vpn)

                    options.joinToString()
                }
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

    override fun onResume() {
        super.onResume()

        presenter.updateServerName()
        presenter.updateUrlStatus()
        updateSecurityLevelSummary()
        potentiallyShowSecurityLevel()
    }

    private fun potentiallyShowSecurityLevel() {
        lifecycleScope.launch {
            findPreference<Preference>("connection_security_level")?.let {
                it.isVisible = USE_NEW_LAUNCHER
            }
        }
    }
    private fun updateSecurityLevelSummary() {
        lifecycleScope.launch {
            findPreference<Preference>("connection_security_level")?.let { preference ->
                val summaryId = when (presenter.getAllowInsecureConnection()) {
                    true -> commonR.string.connection_security_less_secure
                    false -> commonR.string.connection_security_most_secure
                    null -> commonR.string.connection_security_level_default_summary
                }
                preference.summary = getString(summaryId)
            }
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    fun getServerId(): Int = serverId
}
