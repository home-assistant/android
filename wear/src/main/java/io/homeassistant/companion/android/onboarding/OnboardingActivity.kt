package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.widget.WearableRecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
import io.homeassistant.companion.android.onboarding.manual_setup.ManualSetupActivity
import io.homeassistant.companion.android.util.LoadingView
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity(), OnboardingView {

    private lateinit var adapter: ServerListAdapter

    private lateinit var capabilityClient: CapabilityClient
    private lateinit var remoteActivityHelper: RemoteActivityHelper

    companion object {
        private const val TAG = "OnboardingActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: OnboardingPresenter
    private lateinit var loadingView: LoadingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_onboarding)

        loadingView = findViewById(R.id.loading_view)

        adapter = ServerListAdapter(ArrayList())
        adapter.onInstanceClicked = { instance -> presenter.onAdapterItemClick(instance) }
        adapter.onManualSetupClicked = { this.startManualSetup() }
        adapter.onPhoneSignInClicked = { this.startPhoneSignIn() }

        capabilityClient = Wearable.getCapabilityClient(this)
        remoteActivityHelper = RemoteActivityHelper(this)

        findViewById<WearableRecyclerView>(R.id.server_list)?.apply {
            layoutManager = LinearLayoutManager(this@OnboardingActivity)
            isEdgeItemsCenteringEnabled = true
            adapter = this@OnboardingActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()

        loadingView.visibility = View.GONE

        // Add listener to exchange authentication tokens
        Wearable.getDataClient(this).addListener(presenter)

        // Check for current instances
        Thread { findExistingInstances() }.start()

        // Request authentication token in separate task
        Thread { requestInstances() }.start()

        // Check if there is a phone connected that supports sign in
        Thread { requestPhoneSignIn() }.start()
    }

    override fun onPause() {
        super.onPause()

        Wearable.getDataClient(this).removeListener(presenter)
    }

    override fun startAuthentication(flowId: String) {
        startActivity(AuthenticationActivity.newInstance(this, flowId))
    }

    override fun startManualSetup() {
        startActivity(ManualSetupActivity.newInstance(this))
    }

    override fun startPhoneSignIn() {
        try {
            remoteActivityHelper.startRemoteActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    data = Uri.parse("homeassistant://wear-phone-signin")
                },
                null // a Wear device only has one companion device so this is not needed
            )
            val confirmation = Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.OPEN_ON_PHONE_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 2500)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.continue_on_phone))
            }
            startActivity(confirmation)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open sign in activity on phone", e)
            showError()
        }
    }

    override fun showLoading() {
        loadingView.visibility = View.VISIBLE
    }

    override fun showError() {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.failed_connection))
        }
        startActivity(intent)
        loadingView.visibility = View.GONE
    }

    override fun onInstanceFound(instance: HomeAssistantInstance) {
        Log.d(TAG, "onInstanceFound: ${instance.name}")
        if (!adapter.servers.contains(instance)) {
            adapter.servers.add(instance)
            adapter.notifyDataSetChanged()
            Log.d(TAG, "onInstanceFound: added ${instance.name}")
        }
    }

    override fun onInstanceLost(instance: HomeAssistantInstance) {
        if (adapter.servers.contains(instance)) {
            adapter.servers.remove(instance)
            adapter.notifyDataSetChanged()
        }
    }

    private fun findExistingInstances() {
        Log.d(TAG, "findExistingInstances")
        Tasks.await(Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/home_assistant_instance"))).apply {
            Log.d(TAG, "findExistingInstances: success, found ${this.count}")
            this.forEach { item ->
                val instance = presenter.getInstance(DataMapItem.fromDataItem(item).dataMap)
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
            capabilityClient.getCapability(
                "request_home_assistant_instance",
                CapabilityClient.FILTER_REACHABLE
            )
        )

        if (capabilityInfo.nodes.size == 0) {
            Log.d(TAG, "requestInstances: No nodes found")
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

    private fun requestPhoneSignIn() {
        Log.d(TAG, "requestPhoneSignIn")

        // Find all nodes that are capable
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(
                "sign_in_to_home_assistant_instance",
                CapabilityClient.FILTER_REACHABLE
            )
        )

        Log.d(TAG, "requestPhoneSignIn: found ${capabilityInfo.nodes.size} nodes")
        runOnUiThread {
            adapter.phoneSignInAvailable = capabilityInfo.nodes.size > 0
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
