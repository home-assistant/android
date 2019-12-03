package io.homeassistant.companion.android.onboarding.integration

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ViewFlipper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject

class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        private const val LOADING_VIEW = 0
        private const val ERROR_VIEW = 1

        private const val LOCATION_REQUEST_CODE = 1000

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
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.onRegistrationAttempt()
    }

    override fun deviceRegistered() {
        if (!haveLocationPermission()) {
            ActivityCompat.requestPermissions(
                activity!!,
                getLocationPermissions(),
                LOCATION_REQUEST_CODE
            )
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

        if (requestCode == LOCATION_REQUEST_CODE &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            val intent = Intent(context, LocationBroadcastReceiver::class.java)
            intent.action = LocationBroadcastReceiver.ACTION_REQUEST_LOCATION_UPDATES

            activity!!.sendBroadcast(intent)
        }

        (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
    }

    private fun haveLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context!!,
            ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
            context!!,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    private fun getLocationPermissions(): Array<String> {
        var retVal = arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= 21)
            retVal = retVal.plus(ACCESS_BACKGROUND_LOCATION)

        return retVal
    }
}
