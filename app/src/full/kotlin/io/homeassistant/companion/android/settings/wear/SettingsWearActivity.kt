package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.concurrent.futures.await
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel.Companion.CAPABILITY_WEAR_APP
import io.homeassistant.companion.android.settings.wear.views.SettingsWearMainView
import io.homeassistant.companion.android.settings.wear.views.SettingsWearOnboardingView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsWearActivity :
    AppCompatActivity(),
    CapabilityClient.OnCapabilityChangedListener {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    private var capabilityClient: CapabilityClient? = null
    private var nodeClient: NodeClient? = null
    private var remoteActivityHelper: RemoteActivityHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeCompat()

        setContent {
            HomeAssistantAppTheme {
                SettingsWearOnboardingView(
                    settingsWearViewModel,
                    onInstallOnWearDeviceClicked = {
                        openPlayStoreOnWearDevicesWithoutApp()
                    },
                    onFinishInstallOnDevices = {
                        openWearDeviceSettings()
                    },
                    onBackClicked = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }

        capabilityClient = try {
            Wearable.getCapabilityClient(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get capability client")
            null
        }
        nodeClient = try {
            Wearable.getNodeClient(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get node client")
            null
        }
        remoteActivityHelper = try {
            RemoteActivityHelper(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get remote activity helper")
            null
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    // Initial request for devices with our capability, aka, our Wear app installed.
                    settingsWearViewModel.findWearDevicesWithApp(capabilityClient)
                }
                launch {
                    // Initial request for all Wear devices connected (with or without our capability).
                    // Additional Note: Because there isn't a listener for ALL Nodes added/removed from network
                    // that isn't deprecated, we simply update the full list when the Google API Client is
                    // connected and when capability changes come through in the onCapabilityChanged() method.
                    settingsWearViewModel.findAllWearDevices(nodeClient)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        capabilityClient?.removeListener(this, CAPABILITY_WEAR_APP)
    }

    override fun onResume() {
        super.onResume()
        capabilityClient?.addListener(this, CAPABILITY_WEAR_APP)
    }

    /*
     * Updates UI when capabilities change (install/uninstall wear app).
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        settingsWearViewModel.updateWearNodesWithApp(capabilityInfo.nodes)
        lifecycleScope.launch {
            // Because we have an updated list of devices with/without our app, we need to also update
            // our list of active Wear devices.
            settingsWearViewModel.findAllWearDevices(nodeClient)
        }
    }

    private fun openWearDeviceSettings() {
        val wearNodesWithApp = settingsWearViewModel.getNodesWithApp()
        if (wearNodesWithApp.isEmpty()) {
            Timber.d("No wear nodes with app")
        }
        startActivity(
            SettingsWearMainView.newInstance(applicationContext, wearNodesWithApp, getAuthIntentUrl()),
        )
        finish()
    }

    private fun openPlayStoreOnWearDevicesWithoutApp() {
        val nodesWithoutApp = settingsWearViewModel.getNodesWithoutApp()

        Timber.d("Number of nodes without app: " + nodesWithoutApp.size)
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(PLAY_STORE_APP_URI.toUri())

        // In parallel, start remote activity requests for all wear devices that don't have the app installed yet.
        nodesWithoutApp.forEach { node ->
            lifecycleScope.launch {
                try {
                    remoteActivityHelper
                        ?.startRemoteActivity(
                            targetIntent = intent,
                            targetNodeId = node.id,
                        )
                        ?.await()

                    Toast.makeText(
                        this@SettingsWearActivity,
                        getString(commonR.string.store_request_successful),
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (cancellationException: CancellationException) {
                    // Request was cancelled normally
                } catch (throwable: Throwable) {
                    Toast.makeText(
                        this@SettingsWearActivity,
                        getString(commonR.string.store_request_unsuccessful),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun getAuthIntentUrl(): String? {
        return intent.data?.let {
            if (it.scheme == "homeassistant" && it.host == "wear-phone-signin") {
                // Return empty string if phone sign in was used to open this, indicating no instance selected
                it.getQueryParameter("url") ?: ""
            } else {
                null
            }
        }
    }

    companion object {
        private const val PLAY_STORE_APP_URI =
            "market://details?id=io.homeassistant.companion.android"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearActivity::class.java)
        }
    }
}
