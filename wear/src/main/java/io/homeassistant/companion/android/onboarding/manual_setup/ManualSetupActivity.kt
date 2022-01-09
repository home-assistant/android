package io.homeassistant.companion.android.onboarding.manual_setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ActivityManualSetupBinding
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
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
            presenter.onNextClicked(findViewById<EditText>(R.id.server_url).text.toString())
        }
    }

    override fun startAuthentication(flowId: String) {
        startActivity(AuthenticationActivity.newInstance(this, flowId))
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
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(commonR.string.failed_connection))
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
