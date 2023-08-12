package io.homeassistant.companion.android.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.onboarding.phoneinstall.PhoneInstallActivity
import io.homeassistant.companion.android.util.LoadingView
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
@SuppressLint("VisibleForTests") // https://issuetracker.google.com/issues/239451111
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

    private var phoneSignInAvailable = false
    private var phoneInstallOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_onboarding)

        loadingView = findViewById(R.id.loading_view)

        adapter = ServerListAdapter(ArrayList())
        adapter.onInstanceClicked = { instance ->
            if (phoneSignInAvailable) {
                startPhoneSignIn(instance)
            } else {
                presenter.onInstanceClickedWithoutApp(this, instance.url.toString())
            }
        }
        adapter.onManualSetupClicked = {
            if (phoneSignInAvailable) {
                startPhoneSignIn(null)
            } else {
                requestPhoneAppInstall()
            }
        }

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

    private fun requestPhoneAppInstall() = startActivity(PhoneInstallActivity.newInstance(this))

    private fun startPhoneSignIn(instance: HomeAssistantInstance?) {
        lifecycleScope.launch {
            showLoading()
            try {
                val url = "homeassistant://wear-phone-signin${if (instance != null) "?url=${instance.url}" else ""}"
                remoteActivityHelper.startRemoteActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        data = Uri.parse(url)
                    },
                    null // a Wear device only has one companion device so this is not needed
                ).await()
                showContinueOnPhone()
            } catch (e: Exception) {
                if (e is RemoteActivityHelper.RemoteIntentException) {
                    Log.e(TAG, "Unable to open sign in activity on phone with app, falling back to OAuth", e)
                    if (instance != null) {
                        presenter.onInstanceClickedWithoutApp(this@OnboardingActivity, instance.url.toString())
                    } else {
                        requestPhoneAppInstall()
                    }
                } else {
                    Log.e(TAG, "Unable to open sign in activity on phone", e)
                    showError()
                }
            }
        }
    }

    override fun startIntegration(serverId: Int) {
        startActivity(MobileAppIntegrationActivity.newInstance(this, serverId))
    }

    override fun showLoading() {
        loadingView.visibility = View.VISIBLE
    }

    override fun showContinueOnPhone() {
        val confirmation = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.OPEN_ON_PHONE_ANIMATION
            )
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 2000)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.continue_on_phone))
        }
        startActivity(confirmation)
        loadingView.visibility = View.GONE
    }

    override fun showError(@StringRes message: Int?) {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message ?: commonR.string.failed_connection))
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
        phoneSignInAvailable = capabilityInfo.nodes.size > 0

        if (!phoneSignInAvailable && !phoneInstallOpened) {
            phoneInstallOpened = true
            requestPhoneAppInstall()
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
