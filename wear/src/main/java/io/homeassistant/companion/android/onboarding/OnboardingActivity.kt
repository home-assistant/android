package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

class OnboardingActivity : AppCompatActivity(), OnboardingView, DataClient.OnDataChangedListener {

    private lateinit var adapter: ServerListAdapter

    companion object {
        private const val TAG = "OnboardingActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: OnboardingPresenter

    //private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        setContentView(R.layout.activity_onboarding)

        adapter = ServerListAdapter(ArrayList()) { instance -> presenter.onAdapterItemClick(instance) }

        findViewById<WearableRecyclerView>(R.id.server_list)?.apply {
            // To align the edge children (first and last) with the center of the screen
            isEdgeItemsCenteringEnabled = true
            layoutManager = WearableLinearLayoutManager(this@OnboardingActivity)
            adapter = this@OnboardingActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()

        // Add listener to exchange authentication tokens
        Wearable.getDataClient(this).addListener(this)

        // Check for current instances
        Thread { findExistingInstances() }.start()

        // Request authentication token in separate task
        Thread { requestInstances() }.start()
    }

    override fun onPause() {
        super.onPause()

        Wearable.getDataClient(this).removeListener(this)
    }

    override fun startAuthentication(flowId: String) {
        startActivity(AuthenticationActivity.newInstance(this, flowId, "",""))
        //finish()
    }

    private fun findExistingInstances() {
        Log.d(TAG, "findExistingInstances")
        Tasks.await(Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/home_assistant_instance"))).apply {
            Log.d(TAG, "findExistingInstances: success, found ${this.count}")
            this.forEach { item ->
                val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                this@OnboardingActivity.runOnUiThread {
                    onInstanceFound(instance)
                }
            }
        }
    }

    private fun requestInstances() {
        Log.d(TAG, "requestInstances")

        // Find all nodes that are capable
        val capabilityInfo: CapabilityInfo = Tasks.await(
            Wearable.getCapabilityClient(this)
                .getCapability(
                    "request_home_assistant_instance",
                    CapabilityClient.FILTER_REACHABLE
                )
        )

        if (capabilityInfo.nodes.size == 0) {
            Log.d(TAG, "requestInstances: No nodes found")
            //TODO Immediately go to manual setup
        }

        capabilityInfo.nodes.forEach { node ->
            Wearable.getMessageClient(this).sendMessage(
                node.id,
                "/request_home_assistant_instance",
                ByteArray(0)
            ).apply {
                addOnSuccessListener { Log.d(TAG, "requestInstances: request home assistant instances from $node.id: ${node.displayName}") }
                addOnFailureListener { Log.w(TAG, "requestInstances: failed to request home assistant instances from $node.id: ${node.displayName}") }
            }
        }
    }

    private fun getInstance(map: DataMap): HomeAssistantInstance {
        map.apply {
            return HomeAssistantInstance(
                getString("name", ""),
                URL(getString("url", "")),
                getString("version", "")
            )
        }
    }

    private fun adapterOnClick(instance: HomeAssistantInstance) {
        Log.d(TAG, "Clicked on ${instance.name}")

        // Login authentication

        // Register authorization code


        //TODO Request authentication info
        /*
        When authenticating we should call the auth api
        Normally, this is done by redirecting the user to the authorization url (AuthenticationRepositoryImpl.buildAuthenticationUrl())
        For example: http://your-instance.com/auth/authorize?client_id=https%3A%2F%2Fhass-auth-demo.glitch.me&redirect_uri=https%3A%2F%2Fhass-auth-demo.glitch.me%2F%3Fauth_callback%3D1
        The callback url (in the android app: homeassistant://auth-callback) is then picked up by the app
        After logging in, the user will be redirected to the callback url, where the authentication code is passed. This code is then exchanged for a token

        I don't think we can implement a callback url in Wear OS. Therefore, the procedure should be:
        - Request login info from android app (host, username, password)
        - Show login form where user can fill in/change username and password
        - Send filled in username and password back to android app
        - Android app provides the info with the callback url etc. And then receives authorization code
        - Android app requests tokens and sends them to the Wear OS app

        After some testing with curl in a terminal, the following procedure was found:
        - post to https://server.adr/auth/login_flow, with json content: 'client_id':'website_of_app', 'redirect_uri':'valid_uri_but_isnt_used_now', 'handler':['homassistant',null]
        - The return json should have the following fields:
            "type": "form"
            "handler": ["homeassistant", null]
            "step_id": "init"
            "data_schema": [{"type": "string", "name": "username"},{"type": "string", "name": "password"}]
            "errors": {}
            "flow_id": "flow_id_code_to_use"
        - Use the returned flow_id for the next step:
        - post to https://server.adr/auth/login_flow/{flow_id}, with json content: 'client_id':'website_of_app', 'username':'{username}', 'password':'{password}'
        - The return json should have the following fields:
            "version": 1
            "type": "create_entry"
            "flow_id": "same_flow_id"
            "handler": ["homeassistant", null]
            "title": "Home Assistant Local"
            "result": "code_for_authentication"
        - post to https://server.adr/auth/token, with content type application/x-www-form-urlencoded and data: grant_type=authorization_code&code={code_for_authentication}&client_id=website_of_app
        - The return json should have the following fields:
            "token_type": "Bearer"
            "access_token": "ABCDEFGH"
            "expires_in": 1800
            "refresh_token": "IJKLMNOPQRST"
        - Now the tokens can be saved and used for authentication afterwards
        - Next step is device registration
         */
    }

    private fun onInstanceFound(instance: HomeAssistantInstance) {
        Log.d(TAG, "onInstanceFound: ${instance.name}")
        if (!adapter.servers.contains(instance)) {
            adapter.servers.add(instance)
            adapter.notifyDataSetChanged()
            //adapter.notifyItemInserted(adapter.servers.size-1)
            Log.d(TAG, "onInstanceFound: added ${instance.name}")
        }
    }

    private fun onInstanceLost(instance: HomeAssistantInstance) {
        if (adapter.servers.contains(instance)) {
            adapter.servers.remove(instance)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: [${dataEvents.count}]")
        dataEvents.forEach { event ->
            // DataItem changed
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        Log.d(TAG, "onDataChanged: found home_assistant_instance")
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        onInstanceFound(instance)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        onInstanceLost(instance)
                    }
                }
            }
        }
    }

    private fun onSelectManualSetup() {
        //TODO
    }

    private fun onAuthenticationSuccess() {
        //TODO go to register integration
    }
}
