package io.homeassistant.companion.android.onboarding.integration

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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

        private const val IGNORE_OPT_REQUEST = 0

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
            presenter.onGrantedLocationPermission(context!!, activity!!)
            requestBackgroundAccess()
        }
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
            requestBackgroundAccess()
        } else {
            (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        val packageName = activity?.packageName ?: ""
        val intent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context?.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == false
        ) {
            intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity?.packageName}")
            )
            startActivityForResult(intent, IGNORE_OPT_REQUEST)
        } else {
            (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IGNORE_OPT_REQUEST) {
            (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
        }
    }
}
