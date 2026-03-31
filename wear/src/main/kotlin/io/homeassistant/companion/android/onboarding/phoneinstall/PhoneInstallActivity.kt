package io.homeassistant.companion.android.onboarding.phoneinstall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.remote.interactions.RemoteActivityHelper
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.manual.ManualSetupActivity
import io.homeassistant.companion.android.theme.WearAppTheme
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber

class PhoneInstallActivity : AppCompatActivity() {
    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, PhoneInstallActivity::class.java)
        }
    }

    private lateinit var remoteActivityHelper: RemoteActivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WearAppTheme {
                PhoneInstallView(
                    onInstall = ::openPlayStoreOnPhone,
                    onRefresh = {
                        finish() // OnboardingActivity will refresh when resumed
                    },
                    onAdvanced = {
                        startActivity(ManualSetupActivity.newInstance(this@PhoneInstallActivity))
                        finish()
                    },
                )
            }
        }

        remoteActivityHelper = RemoteActivityHelper(this)
    }

    private fun openPlayStoreOnPhone() {
        lifecycleScope.launch {
            var success = true
            try {
                remoteActivityHelper.startRemoteActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        data = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}".toUri()
                    },
                    // A Wear device only has one companion device so this is not needed
                    null,
                ).await()
            } catch (e: Exception) {
                Timber.e(e, "Unable to open remote activity")
                success = false
            }
            val confirmation =
                Intent(this@PhoneInstallActivity, ConfirmationActivity::class.java).apply {
                    putExtra(
                        ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        if (success) {
                            ConfirmationActivity.OPEN_ON_PHONE_ANIMATION
                        } else {
                            ConfirmationActivity.FAILURE_ANIMATION
                        },
                    )
                    if (success) {
                        putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 2000)
                    }
                    putExtra(
                        ConfirmationActivity.EXTRA_MESSAGE,
                        getString(
                            if (success) {
                                commonR.string.continue_on_phone
                            } else {
                                commonR.string.failed_phone_connection
                            },
                        ),
                    )
                }
            startActivity(confirmation)
        }
    }
}
