package io.homeassistant.companion.android.onboarding.manual_setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ActivityManualSetupBinding
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManualSetupActivity : AppCompatActivity(), ManualSetupView {
    companion object {
        private const val TAG = "ManualSetupActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ManualSetupActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: ManualSetupPresenter
    private lateinit var binding: ActivityManualSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityManualSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonNext.setOnClickListener {
            presenter.onNextClicked(this, findViewById<EditText>(R.id.server_url).text.toString())
        }
    }

    override fun startIntegration() {
        startActivity(MobileAppIntegrationActivity.newInstance(this))
    }

    override fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
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
        binding.loadingView.visibility = View.GONE
    }

    override fun showError(@StringRes message: Int) {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.FAILURE_ANIMATION
            )
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message))
        }
        startActivity(intent)
        binding.loadingView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        binding.loadingView.visibility = View.GONE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
