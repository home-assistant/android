package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import kotlinx.android.synthetic.main.activity_authentication.*
import kotlinx.android.synthetic.main.activity_integration.*
import kotlinx.android.synthetic.main.activity_integration.loading_view
import javax.inject.Inject

class MobileAppIntegrationActivity : AppCompatActivity(), MobileAppIntegrationView {
    companion object {
        private const val TAG = "MobileAppIntegrationActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, MobileAppIntegrationActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        setContentView(R.layout.activity_integration)

        device_name.setText(Build.MODEL)

        findViewById<FloatingActionButton>(R.id.finish).setOnClickListener {
            presenter.onRegistrationAttempt(device_name.text.toString())
        }
    }

    override fun deviceRegistered() {
        val intent = HomeActivity.newInstance(this)
        // empty the back stack
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        loading_view.visibility = View.GONE
    }

    override fun showLoading() {
        loading_view.visibility = View.VISIBLE
    }

    override fun showError() {
        // Hide loading view again
        loading_view.visibility = View.GONE

        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.failed_registration))
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}