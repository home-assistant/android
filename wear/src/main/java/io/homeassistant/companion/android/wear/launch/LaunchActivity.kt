package io.homeassistant.companion.android.wear.launch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.support.wearable.phone.PhoneDeviceType
import androidx.core.view.isVisible
import com.google.android.wearable.intent.RemoteIntent
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.ActivityLaunchBinding
import io.homeassistant.companion.android.wear.navigation.NavigationActivity
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import javax.inject.Inject

class LaunchActivity : WearableActivity(), LaunchView {

    companion object {
        private const val PLAY_STORE_HOME_ASSISTANT = "market://details?id=io.homeassistant.companion.android"
    }

    @Inject lateinit var launchPresenter: LaunchPresenter

    private val binding by viewBinding(ActivityLaunchBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAmbientEnabled()
        setContentView(binding.root)

        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), this)
            .inject(this)

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

    override fun showActionButton(message: Int?, icon: Int?, action: (() -> Unit)?) {
        binding.actionButton.isVisible = message != null
        if (message != null && icon != null) {
            binding.actionButtonText.text = getString(message)
            binding.actionButtonText.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            binding.actionButton.setOnClickListener { action?.invoke() }
        } else {
            binding.actionButton.setOnClickListener(null)
        }
    }

    override fun displayUnreachable() {
        showActionButton(R.string.launch_store, R.drawable.ic_launch) {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(PLAY_STORE_HOME_ASSISTANT))
            RemoteIntent.startRemoteActivity(this, intent, null)
        }
        setStateInfo(R.string.ha_phone_app_not_reachable)
    }

    override fun displayRetryActionButton(stateMessage: Int) {
        showActionButton(R.string.retry, R.drawable.ic_reload) {
            setStateInfo(null)
            showActionButton(null)
            launchPresenter.onRefresh()
        }
        setStateInfo(stateMessage)
    }

    override fun displayNextScreen() {
        startActivity(Intent(this, NavigationActivity::class.java))
        finishAffinity()
    }

    override fun onDestroy() {
        launchPresenter.onFinish()
        super.onDestroy()
    }

}