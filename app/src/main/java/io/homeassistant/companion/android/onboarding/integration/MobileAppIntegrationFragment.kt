package io.homeassistant.companion.android.onboarding.integration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject


class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        fun newInstance(): MobileAppIntegrationFragment {
            return MobileAppIntegrationFragment()
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mobile_app_integration, container, false).apply {
            findViewById<Button>(R.id.skip).setOnClickListener {
                presenter.onSkip()
            }
            findViewById<Button>(R.id.retry).setOnClickListener {
                view!!.findViewById<ProgressBar>(R.id.progress).visibility = ProgressBar.VISIBLE
                presenter.onRetry()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.onRetry()
    }

    override fun deviceRegistered() {
        (activity as MobileAppIntegrationListener).integrationRegistrationComplete()
    }

    override fun registrationFailed() {
        view!!.findViewById<AppCompatTextView>(R.id.mobile_app_status)
            .setText(R.string.error_with_registration)

        view!!.findViewById<ProgressBar>(R.id.progress).visibility = ProgressBar.INVISIBLE
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

}

