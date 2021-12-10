package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ActivitySettingsWearBinding
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

    private lateinit var capabilityClient: CapabilityClient
    private lateinit var nodeClient: NodeClient
    private lateinit var remoteActivityHelper: RemoteActivityHelper

    private var wearNodesWithApp: Set<Node>? = null
    private var allConnectedNodes: List<Node>? = null

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_settings_wear, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/wear-os/wear-os"))
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsWearBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        capabilityClient = Wearable.getCapabilityClient(this)
        nodeClient = Wearable.getNodeClient(this)
        remoteActivityHelper = RemoteActivityHelper(this)

        binding.remoteOpenButton.setOnClickListener {
            openPlayStoreOnWearDevicesWithoutApp()
        }

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
        capabilityClient.removeListener(this, CAPABILITY_WEAR_APP)
    }

    override fun onResume() {
        super.onResume()
        capabilityClient.addListener(this, CAPABILITY_WEAR_APP)
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
                .getCapability(CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL)
                .await()

            withContext(Dispatchers.Main) {
                wearNodesWithApp = capabilityInfo.nodes
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
            val connectedNodes = nodeClient.connectedNodes.await()

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
                binding.informationTextView.text = getString(commonR.string.message_checking)
                binding.remoteOpenButton.isInvisible = true
            }
            wearNodesWithApp.isEmpty() -> {
                Log.d(TAG, "Missing on all devices")
                binding.informationTextView.text = getString(commonR.string.message_missing_all)
                binding.remoteOpenButton.isVisible = true
            }
            wearNodesWithApp.size < allConnectedNodes.size -> {
                Log.d(TAG, "Installed on some devices")
                startActivity(SettingsWearMainView.newInstance(applicationContext, wearNodesWithApp))
                finish()
            }
            else -> {
                Log.d(TAG, "Installed on all devices")
                startActivity(SettingsWearMainView.newInstance(applicationContext, wearNodesWithApp))
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
                        .startRemoteActivity(
                            targetIntent = intent,
                            targetNodeId = node.id
                        )
                        .await()

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
