package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import javax.inject.Inject

class HomeActivity : AppCompatActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    companion object {
        private const val TAG = "HomeActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            presenter.onLogoutClicked()
        }

        presenter.onViewReady()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun showHomeAssistantVersion(version: String) {
        val txtVersion = findViewById<TextView>(R.id.txt_version)
        txtVersion.text = getString(R.string.version, version)
    }

    override fun showEntitiesCount(count: Int) {
        val txtEntities = findViewById<TextView>(R.id.txt_entities)
        txtEntities.text = resources.getQuantityString(R.plurals.entities_found, count, count)
    }

    override fun displayOnBoarding() {
        val intent = OnboardingActivity.newInstance(this)
        startActivity(intent)
        finish()
    }

    override fun displayMobileAppIntegration() {
        val intent = MobileAppIntegrationActivity.newInstance(this)
        startActivity(intent)
        finish()
    }
}
