package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.databinding.ActivityIntegrationBinding
import io.homeassistant.companion.android.home.HomeActivity
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class MobileAppIntegrationActivity : AppCompatActivity(), MobileAppIntegrationView {
    companion object {
        private const val TAG = "MobileAppIntegrationActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, MobileAppIntegrationActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter
    private lateinit var binding: ActivityIntegrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIntegrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.serverUrl.setText(Build.MODEL)

        binding.finish.setOnClickListener {
            presenter.onRegistrationAttempt(binding.serverUrl.text.toString())
        }
    }

    override fun onResume() {
        super.onResume()

        binding.loadingView.visibility = View.GONE
    }

    override fun deviceRegistered() {
        val intent = HomeActivity.newInstance(this)
        // empty the back stack
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
    }

    override fun showError() {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.FAILURE_ANIMATION
            )
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.failed_registration))
        }
        startActivity(intent)
        binding.loadingView.visibility = View.GONE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
