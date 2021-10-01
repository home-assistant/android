package io.homeassistant.companion.android.onboarding.manual_setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
import io.homeassistant.companion.android.util.LoadingView
import javax.inject.Inject

class ManualSetupActivity : AppCompatActivity(), ManualSetupView {
    companion object {
        private const val TAG = "ManualSetupActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ManualSetupActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: ManualSetupPresenter
    private lateinit var loadingView: LoadingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        setContentView(R.layout.activity_manual_setup)

        loadingView = findViewById<LoadingView>(R.id.loading_view)

        findViewById<FloatingActionButton>(R.id.button_next).setOnClickListener {
            presenter.onNextClicked(findViewById<EditText>(R.id.server_url).text.toString())
        }
    }

    override fun startAuthentication(flowId: String) {
        startActivity(AuthenticationActivity.newInstance(this, flowId))
    }

    override fun showLoading() {
        loadingView.visibility = View.VISIBLE
    }

    override fun showError() {
        // Show failure message
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.failed_connection))
        }
        startActivity(intent)
        loadingView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        loadingView.visibility = View.GONE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
