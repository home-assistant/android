package io.homeassistant.companion.android.onboarding.authentication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ActivityAuthenticationBinding
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import javax.inject.Inject

@AndroidEntryPoint
class AuthenticationActivity : AppCompatActivity(), AuthenticationView {
    companion object {
        private const val TAG = "AuthenticationActivity"

        fun newInstance(context: Context, flowId: String): Intent {
            var intent = Intent(context, AuthenticationActivity::class.java)
            intent.putExtra("flowId", flowId)
            return intent
        }
    }

    @Inject
    lateinit var presenter: AuthenticationPresenter
    private lateinit var binding: ActivityAuthenticationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null || !intent.hasExtra("flowId")) {
            Log.e(TAG, "Flow id not specified, canceling authentication")
            finish()
        }

        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonNext.setOnClickListener {
            presenter.onNextClicked(
                intent.getStringExtra("flowId")!!,
                binding.username.text.toString(),
                binding.password.text.toString()
            )
        }
    }

    override fun onResume() {
        super.onResume()

        binding.loadingView.visibility = View.GONE
    }

    override fun startIntegration() {
        startActivity(MobileAppIntegrationActivity.newInstance(this))
    }

    override fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
    }

    override fun showError() {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.failed_authentication))
        }
        startActivity(intent)
        binding.loadingView.visibility = View.GONE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
