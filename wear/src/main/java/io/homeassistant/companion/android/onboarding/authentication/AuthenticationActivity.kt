package io.homeassistant.companion.android.onboarding.authentication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import kotlinx.android.synthetic.main.activity_authentication.*
import javax.inject.Inject

class AuthenticationActivity : AppCompatActivity(), AuthenticationView {
    companion object {
        private const val TAG = "AuthenticationActivity"

        fun newInstance(context: Context, flowId: String, username: String, password: String): Intent {
            var intent = Intent(context, AuthenticationActivity::class.java)
            intent.putExtra("flowId", flowId)
            intent.putExtra("username", username)
            intent.putExtra("password", password)
            return intent
        }
    }

    @Inject
    lateinit var presenter: AuthenticationPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        setContentView(R.layout.activity_authentication)

        if (intent == null) {
            Log.e(TAG, "intent data does not exist, canceling authentication")
            finish()
        } else {
            // Set login info from intent
            intent.getStringExtra("username").also { username.setText(it) }
            intent.getStringExtra("password").also { password.setText(it) }

            button_next.setOnClickListener {
                presenter.onNextClicked(
                    intent.getStringExtra("flowId")!!,
                    username.text.toString(),
                    password.text.toString())
            }
        }
    }

    override fun startIntegration() {
        startActivity(MobileAppIntegrationActivity.newInstance(this))
    }
}