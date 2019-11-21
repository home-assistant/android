package io.homeassistant.companion.android.onboarding.integration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_mobile_app_integration.*

class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        private const val LOADING_VIEW = 0
        private const val ERROR_VIEW = 1

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
        return inflater.inflate(R.layout.fragment_mobile_app_integration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        skipButton.setOnClickListener { presenter.onSkip() }
        retryButton.setOnClickListener { presenter.onRegistrationAttempt() }
    }

    override fun onResume() {
        super.onResume()
        presenter.onRegistrationAttempt()
    }

    override fun deviceRegistered() {
        (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
    }

    override fun registrationSkipped() {
        (activity as MobileAppIntegrationListener).onIntegrationRegistrationSkipped()
    }

    override fun showError() {
        flipperView.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        flipperView.displayedChild = LOADING_VIEW
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
