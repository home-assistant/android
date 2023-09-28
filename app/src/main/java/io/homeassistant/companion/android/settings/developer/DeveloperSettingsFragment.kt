package io.homeassistant.companion.android.settings.developer

import android.content.IntentSender
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.developer.location.LocationTrackingFragment
import io.homeassistant.companion.android.settings.log.LogFragment
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class DeveloperSettingsFragment : DeveloperSettingsView, PreferenceFragmentCompat() {

    @Inject
    lateinit var presenter: DeveloperSettingsPresenter

    private var threadDebugDialog: AlertDialog? = null
    private var threadIntentServer: Int = ServerManager.SERVER_ID_ACTIVE
    private var threadIntentDeviceOnly: Boolean = true

    private val threadPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        presenter.onThreadPermissionResult(requireContext(), result, threadIntentServer, threadIntentDeviceOnly)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        presenter.init(this)

        preferenceManager.preferenceDataStore = presenter.getPreferenceDataStore()

        setPreferencesFromResource(R.xml.preferences_developer, rootKey)

        findPreference<Preference>("show_share_logs")?.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.content, LogFragment::class.java, null)
                addToBackStack(getString(io.homeassistant.companion.android.common.R.string.log))
            }
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("location_tracking")?.let {
            it.isVisible = BuildConfig.FLAVOR == "full"
            it.setOnPreferenceClickListener {
                parentFragmentManager.commit {
                    replace(R.id.content, LocationTrackingFragment::class.java, null)
                    addToBackStack(getString(io.homeassistant.companion.android.common.R.string.location_tracking))
                }
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<Preference>("thread_debug")?.let {
            it.isVisible = presenter.appSupportsThread()
            it.setOnPreferenceClickListener {
                if (presenter.hasMultipleServers()) {
                    parentFragmentManager.setFragmentResultListener(ServerChooserFragment.RESULT_KEY, this) { _, bundle ->
                        if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                            startThreadDebug(bundle.getInt(ServerChooserFragment.RESULT_SERVER))
                        }
                        parentFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
                    }
                    ServerChooserFragment().show(parentFragmentManager, ServerChooserFragment.TAG)
                } else {
                    startThreadDebug(ServerManager.SERVER_ID_ACTIVE)
                }
                return@setOnPreferenceClickListener true
            }
        }
    }

    private fun startThreadDebug(serverId: Int) {
        presenter.runThreadDebug(requireContext(), serverId)
        threadDebugDialog = AlertDialog.Builder(requireContext())
            .setMessage(commonR.string.thread_debug_active)
            .setCancelable(false)
            .create()
        threadDebugDialog?.show()
    }

    override fun onThreadPermissionRequest(intent: IntentSender, serverId: Int, isDeviceOnly: Boolean) {
        threadIntentServer = serverId
        threadIntentDeviceOnly = isDeviceOnly
        threadPermissionLauncher.launch(IntentSenderRequest.Builder(intent).build())
    }

    override fun onThreadDebugResult(result: String, success: Boolean?) {
        threadDebugDialog?.hide()
        AlertDialog.Builder(requireContext())
            .setTitle(commonR.string.thread_debug)
            .setMessage("${if (success == true) "✅" else if (success == null) "⚠️" else "⛔"}\n\n$result")
            .setPositiveButton(commonR.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.troubleshooting)
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
