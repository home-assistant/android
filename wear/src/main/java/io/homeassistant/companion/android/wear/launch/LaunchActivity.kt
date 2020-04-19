package io.homeassistant.companion.android.wear.launch

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.support.wearable.phone.PhoneDeviceType
import android.util.Log
import androidx.core.view.isVisible
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.wearable.intent.RemoteIntent
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.ActivityLaunchBinding
import io.homeassistant.companion.android.wear.launch.LaunchPresenterImpl.Companion.CONFIG_PATH
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.await
import io.homeassistant.companion.android.wear.util.extensions.catch
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import javax.inject.Inject

class LaunchActivity : WearableActivity(), LaunchView {

    companion object {
        private const val CAPABILITY_PHONE_APP = "verify_home_assistant_phone_app_installed"
        private const val PLAY_STORE_HOME_ASSISTANT = "market://details?id=io.homeassistant.companion.android"
    }

    @Inject lateinit var launchPresenter: LaunchPresenter

    private val binding by viewBinding(ActivityLaunchBinding::inflate)
    private val messageClient by lazy { Wearable.getMessageClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAmbientEnabled()
        setContentView(binding.root)

        DaggerPresenterComponent
            .builder()
            .appComponent(appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        messageClient.addListener(launchPresenter)

        when (PhoneDeviceType.getPhoneDeviceType(this@LaunchActivity)) {
            PhoneDeviceType.DEVICE_TYPE_ANDROID -> launchPresenter.onViewReady()
            PhoneDeviceType.DEVICE_TYPE_IOS -> setStateInfo(R.string.ha_wear_not_supporting_apple)
            else -> setStateInfo(R.string.ha_wear_device_type_error)
        }
    }

    override fun showProgressBar(show: Boolean) {
        binding.progressBar.isVisible = show
    }

    override fun setStateInfo(message: Int?) {
        binding.stateInfo.isVisible = message != null
        binding.stateInfo.text = if (message != null) getString(message) else null
    }

    override fun showActionButton(message: Int?, icon: Int?, action: (Activity.() -> Unit)?) {
        binding.actionButton.isVisible = message != null
        if (message != null && icon != null) {
            binding.actionButtonText.text = getString(message)
            binding.actionButtonText.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            binding.actionButton.setOnClickListener { action?.invoke(this) }
        } else {
            binding.actionButton.setOnClickListener(null)
        }
    }

    override fun displayUnreachable() {
        showProgressBar(false)
        showActionButton(R.string.launch_store, R.drawable.ic_launch) {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(PLAY_STORE_HOME_ASSISTANT))
            RemoteIntent.startRemoteActivity(this, intent, null)
        }
        setStateInfo(R.string.ha_phone_app_not_reachable)
    }

    override fun displayInactiveSession() {
        showProgressBar(false)
        setStateInfo(R.string.ha_session_inactive)
    }

    override fun displayNextScreen() {

    }

    override suspend fun getNodeWithInstalledApp(): Node? {
        val client = Wearable.getCapabilityClient(this)
        val capabilityInfo = catch { client.getCapability(CAPABILITY_PHONE_APP, CapabilityClient.FILTER_ALL).await() }
        val nodes: MutableSet<Node> = capabilityInfo?.nodes ?: return null
        return nodes.find { node -> node.isNearby }
    }

    override suspend fun sendMessage(nodeId: String) {
        val messageClient = Wearable.getMessageClient(this)
        val result = catch { messageClient.sendMessage(nodeId, CONFIG_PATH, null).await() }
        Log.d("MessageID", "ID: $result")
    }

    override fun onDestroy() {
        messageClient.removeListener(launchPresenter)
        launchPresenter.onFinish()
        super.onDestroy()
    }

}