package io.homeassistant.companion.android.onboarding.integration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ViewFlipper
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject

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

    private lateinit var viewFlipper: ViewFlipper

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
            viewFlipper = this.findViewById(R.id.view_flipper)
            findViewById<Button>(R.id.skip).setOnClickListener {
                presenter.onSkip()
            }
            findViewById<Button>(R.id.retry).setOnClickListener {
                presenter.onRegistrationAttempt()
            }

            presenter.onRegistrationAttempt()
        }
    }

    override fun deviceRegistered() {
        if (!PermissionManager.hasLocationPermissions(context!!)) {
            PermissionManager.requestLocationPermissions(this)
        } else {
            // If we have permission already we can just continue with
            (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
        }
    }

    override fun registrationSkipped() {
        (activity as MobileAppIntegrationListener).onIntegrationRegistrationSkipped()
    }

    override fun showError() {
        viewFlipper.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        viewFlipper.displayedChild = LOADING_VIEW
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionManager.validateLocationPermissions(requestCode, permissions, grantResults)) {
            presenter.onGrantedLocationPermission(context!!, activity!!)
        }

        (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
    }
}
