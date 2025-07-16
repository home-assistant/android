package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.databinding.ActivityIntegrationBinding
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.util.adjustInset
import javax.inject.Inject

@AndroidEntryPoint
class MobileAppIntegrationActivity :
    AppCompatActivity(),
    MobileAppIntegrationView {
    companion object {
        const val EXTRA_SERVER = "server"

        fun newInstance(context: Context, serverId: Int): Intent {
            return Intent(context, MobileAppIntegrationActivity::class.java).apply {
                putExtra(EXTRA_SERVER, serverId)
            }
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter
    private lateinit var binding: ActivityIntegrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverId = intent.getIntExtra(EXTRA_SERVER, 0)
        if (serverId == 0) finish()

        binding = ActivityIntegrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.deviceName.setText(Build.MODEL)

        binding.finish.setOnClickListener {
            presenter.onRegistrationAttempt(serverId, binding.deviceName.text.toString())
        }

        adjustInset(applicationContext, binding, null)
    }

    override fun onResume() {
        super.onResume()

        binding.loadingView.visibility = View.GONE
        binding.constraintLayout.visibility = View.VISIBLE
    }

    override fun deviceRegistered() {
        val intent = HomeActivity.newInstance(this, fromOnboarding = true)
        // empty the back stack
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
        binding.constraintLayout.visibility = View.GONE
    }

    override fun showError() {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.FAILURE_ANIMATION,
            )
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.failed_registration))
        }
        startActivity(intent)
        binding.loadingView.visibility = View.GONE
        binding.constraintLayout.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
