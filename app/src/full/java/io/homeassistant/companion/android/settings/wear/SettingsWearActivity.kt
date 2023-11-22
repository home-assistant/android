package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.homeassistant.companion.android.databinding.ActivitySettingsWearBinding
import io.homeassistant.companion.android.settings.HelpMenuProvider
import io.homeassistant.companion.android.settings.wear.views.SettingsWearMainView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import io.homeassistant.companion.android.common.R as commonR

class SettingsWearActivity : AppCompatActivity(), CapabilityClient.OnCapabilityChangedListener {

    private lateinit var binding: ActivitySettingsWearBinding

    private var capabilityClient: CapabilityClient? = null
    private var nodeClient: NodeClient? = null
    private var remoteActivityHelper: RemoteActivityHelper? = null

    private var wearNodesWithApp: Set<Node>? = null
    private var allConnectedNodes: List<Node>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsWearBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        capabilityClient = try {
            Wearable.getCapabilityClient(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get capability client", e)
            null
        }
        nodeClient = try {
            Wearable.getNodeClient(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get node client", e)
            null
        }
        remoteActivityHelper = try {
            RemoteActivityHelper(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get remote activity helper", e)
            null
        }

        binding.remoteOpenButton.setOnClickListener {
            openPlayStoreOnWearDevicesWithoutApp()
        }

        addMenuProvider(HelpMenuProvider("https://companion.home-assistant.io/docs/wear-os/wear-os".toUri()))

        // Perform the initial update of the UI
        updateUI()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    // Initial request for devices with our capability, aka, our Wear app installed.
                    findWearDevicesWithApp()
                }
                launch {
                    // Initial request for all Wear devices connected (with or without our capability).
                    // Additional Note: Because there isn't a listener for ALL Nodes added/removed from network
                    // that isn't deprecated, we simply update the full list when the Google API Client is
                    // connected and when capability changes come through in the onCapabilityChanged() method.
                    findAllWearDevices()
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
        title = getString(commonR.string.wear_os_settings_title)
        capabilityClient?.addListener(this, CAPABILITY_WEAR_APP)
    }

    /*
     * Updates UI when capabilities change (install/uninstall wear app).
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        wearNodesWithApp = capabilityInfo.nodes

        lifecycleScope.launch {
            // Because we have an updated list of devices with/without our app, we need to also update
            // our list of active Wear devices.
            findAllWearDevices()
        }
    }

    private suspend fun findWearDevicesWithApp() {
        try {
            val capabilityInfo = capabilityClient
                ?.getCapability(CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
                ?.await()

            withContext(Dispatchers.Main) {
                wearNodesWithApp = capabilityInfo?.nodes
                Log.d(TAG, "Capable Nodes: $wearNodesWithApp")
                updateUI()
            }
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
            throw cancellationException
        } catch (throwable: Throwable) {
            Log.d(TAG, "Capability request failed to return any results.")
        }
    }

    private suspend fun findAllWearDevices() {
        try {
            val connectedNodes = nodeClient?.connectedNodes?.await()

            withContext(Dispatchers.Main) {
                allConnectedNodes = connectedNodes
                updateUI()
            }
        } catch (cancellationException: CancellationException) {
            // Request was cancelled normally
        } catch (throwable: Throwable) {
            Log.d(TAG, "Node request failed to return any results.")
        }
    }

    private fun updateUI() {
        val wearNodesWithApp = wearNodesWithApp
        val allConnectedNodes = allConnectedNodes

        when {
            wearNodesWithApp == null || allConnectedNodes == null -> {
                Log.d(TAG, "Waiting on Results for both connected nodes and nodes with app")
                binding.informationTextView.text = getString(commonR.string.message_checking)
                binding.remoteOpenButton.isInvisible = true
            }
            allConnectedNodes.isEmpty() -> {
                Log.d(TAG, "No devices")
                binding.informationTextView.text = getString(commonR.string.message_no_connected_nodes)
                binding.remoteOpenButton.isInvisible = true
            }
            wearNodesWithApp.isEmpty() -> {
                Log.d(TAG, "Missing on all devices")
                binding.informationTextView.text = getString(commonR.string.message_missing_all)
                binding.remoteOpenButton.isVisible = true
            }
            wearNodesWithApp.size < allConnectedNodes.size -> {
                Log.d(TAG, "Installed on some devices")
                startActivity(SettingsWearMainView.newInstance(applicationContext, wearNodesWithApp, getAuthIntentUrl()))
                finish()
            }
            else -> {
                Log.d(TAG, "Installed on all devices")
                startActivity(SettingsWearMainView.newInstance(applicationContext, wearNodesWithApp, getAuthIntentUrl()))
                finish()
            }
        }
    }

    private fun openPlayStoreOnWearDevicesWithoutApp() {
        val wearNodesWithApp = wearNodesWithApp ?: return
        val allConnectedNodes = allConnectedNodes ?: return

        // Determine the list of nodes (wear devices) that don't have the app installed yet.
        val nodesWithoutApp = allConnectedNodes - wearNodesWithApp

        Log.d(TAG, "Number of nodes without app: " + nodesWithoutApp.size)
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(PLAY_STORE_APP_URI))

        // In parallel, start remote activity requests for all wear devices that don't have the app installed yet.
        nodesWithoutApp.forEach { node ->
            lifecycleScope.launch {
                try {
                    remoteActivityHelper
                        ?.startRemoteActivity(
                            targetIntent = intent,
                            targetNodeId = node.id
                        )
                        ?.await()

                    Toast.makeText(
                        this@SettingsWearActivity,
                        getString(commonR.string.store_request_successful),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (cancellationException: CancellationException) {
                    // Request was cancelled normally
                } catch (throwable: Throwable) {
                    Toast.makeText(
                        this@SettingsWearActivity,
                        getString(commonR.string.store_request_unsuccessful),
                        Toast.LENGTH_LONG
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
        private const val TAG = "SettingsWearAct"

        // Name of capability listed in Wear app's wear.xml.
        // IMPORTANT NOTE: This should be named differently than your Phone app's capability.
        private const val CAPABILITY_WEAR_APP = "verify_wear_app"

        private const val PLAY_STORE_APP_URI =
            "market://details?id=io.homeassistant.companion.android"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearActivity::class.java)
        }
    }
}
