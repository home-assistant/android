package io.homeassistant.companion.android.onboarding.manual

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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.databinding.ActivityManualSetupBinding
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.util.adjustInset
import javax.inject.Inject

@AndroidEntryPoint
class ManualSetupActivity :
    AppCompatActivity(),
    ManualSetupView {
    companion object {
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
            presenter.onNextClicked(this, findViewById<EditText>(R.id.device_name).text.toString())
        }

        adjustInset(applicationContext, null, binding)
    }

    override fun startIntegration(serverId: Int) {
        startActivity(MobileAppIntegrationActivity.newInstance(this, serverId))
    }

    override fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
        binding.constraintLayout.visibility = View.GONE
    }

    override fun showContinueOnPhone() {
        val confirmation = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.OPEN_ON_PHONE_ANIMATION,
            )
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 2000)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.continue_on_phone))
        }
        startActivity(confirmation)
        binding.loadingView.visibility = View.GONE
        binding.constraintLayout.visibility = View.VISIBLE
    }

    override fun showError(@StringRes message: Int) {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.FAILURE_ANIMATION,
            )
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message))
        }
        startActivity(intent)
        binding.loadingView.visibility = View.GONE
        binding.constraintLayout.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()

        binding.loadingView.visibility = View.GONE
        binding.constraintLayout.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
