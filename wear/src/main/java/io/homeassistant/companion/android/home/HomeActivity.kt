package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.impl.Session
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : WearableActivity(),
        MessageClient.OnMessageReceivedListener,
        DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "OnboardingActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        Log.d("Home", "onCreate: ${BuildConfig.APPLICATION_ID}")

        // Enables Always-on
        setAmbientEnabled()
    }

    override fun onResume() {
        super.onResume()

        // Add listener to exchange authentication tokens
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)

        // Request authentication token in separate task
        Thread(Runnable { requestAuthenticationToken() }).start()
    }

    override fun onPause() {
        super.onPause()

        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun requestAuthenticationToken() {
        // Desired flow:
        // - Check if local token is available
        //      - If token is valid => login => DONE
        //      - If token is invalid => try to refresh
        // - If not possible to login
        //      - Check in DataClient for all data items with server addresses
        //      - Put server addresses in list (with node id)
        //      - Send message to all nodes to request updated server addresses
        //      - If any data is received, update the item in the list based on node id
        // - If user selects one of the servers in the list
        //      - Send message to the specific node to share authentication info
        //      - Node shares authentication info if available or error otherwise
        //      - If authentication info available: try to login => DONE
        //      - If login failed or authentication info not available: ask user to login on phone
        // - If user selects "other" from list, or is asked to login on phone
        //      - Field to enter server opens on phone if server was not yet selected
        //      - Webkit window to login screen opens on phone
        //      - If login successful: pass authentication info to wear => DONE
        //      - If login not successful: pass error message to wear => ERROR
        Log.d("Home", "requestAuthenticationToken")

        var validSession: Session? = null
        Tasks.await(Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/authentication_token"))).apply {
            Log.d("Home", "requestAuthenticationToken: success, found ${this.count}")
            this.forEach { item ->
                val session = getSession(DataMapItem.fromDataItem(item).dataMap)
                Log.d("Home", "requestAuthenticationToken: ${session.isExpired()} expires at ${session.expiresTimestamp}")
                if (!session.isExpired()) {
                    validSession = session
                    Log.d("Home", "requestAuthenticationToken: valid session set ${validSession != null}")
                }
            }
        }

        if (validSession != null) {
            Log.d("Home", "requestAuthenticationToken: valid session found")
            runOnUiThread {
                handleSession(validSession!!)
            }
        } else {
            // Send request for authentication token
            val capabilityInfo: CapabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(this)
                    .getCapability(
                        "request_authentication_token",
                        CapabilityClient.FILTER_REACHABLE
                    )
            )
            sendAuthenticationTokenRequest(capabilityInfo)
        }
    }

    private fun sendAuthenticationTokenRequest(capabilityInfo: CapabilityInfo) {
        val nodeId: String? = pickBestNodeId(capabilityInfo.nodes)

        if (nodeId == null) {
            this.text.text = "No phone connected"
        }

        nodeId?.also { nodeId ->
            val sendTask: Task<*> = Wearable.getMessageClient(this).sendMessage(
                nodeId,
                "/request_authentication_token",
                ByteArray(0)
            ).apply {
                addOnSuccessListener { this@HomeActivity.text.text = "Waiting for token $nodeId" }
                addOnFailureListener { this@HomeActivity.text.text = "Can't connect to phone" }
            }
        }
    }

    private fun pickBestNodeId(nodes: Set<Node>): String? {
        Log.d("Home", "pickBestNodeId: $nodes")
        // Find a nearby node or pick one arbitrarily
        return nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("Home", "onMessageReceived: $event")

        if (event.path == "/authentication_token") {
            this.text.text = String(event.data)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("Home", "onDataChanged: [$dataEvents.count]")
        dataEvents.forEach { event ->
            // DataItem changed
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/authentication_token") == 0) {
                        val session = getSession(DataMapItem.fromDataItem(item).dataMap)
                        handleSession(session)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                // Session no longer valid
                // TODO handle
            }
        }
    }

    private fun getSession(map: DataMap): Session {
        map.apply {
            return Session(
                getString("access_token", ""),
                getLong("expires_timestamp"),
                getString("refresh_token", ""),
                getString("token_type", "")
            )
        }
    }

    private fun handleSession(session: Session) {
        this.text.text = session.accessToken
    }
}
