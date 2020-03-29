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
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
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
        private const val SETTINGS_VIEW = 2

        private const val BACKGROUND_REQUEST = 99

        fun newInstance(): MobileAppIntegrationFragment {
            return MobileAppIntegrationFragment()
        }
    }

    @Inject
    lateinit var presenter: MobileAppIntegrationPresenter

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var zoneTracking: SwitchCompat
    private lateinit var zoneTrackingSummary: AppCompatTextView
    private lateinit var backgroundTracking: SwitchCompat
    private lateinit var backgroundTrackingSummary: AppCompatTextView

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

            findViewById<AppCompatButton>(R.id.location_perms).apply {
                setOnClickListener {
                    PermissionManager.requestLocationPermissions(this@MobileAppIntegrationFragment)
                }
            }

            val hasLocationPermission = PermissionManager.hasLocationPermissions(context)

            zoneTracking = findViewById<SwitchCompat>(R.id.location_zone).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    presenter.onToggleZoneTracking(isChecked)
                }
                isEnabled = hasLocationPermission
                isChecked = hasLocationPermission
            }
            zoneTrackingSummary = findViewById(R.id.location_zone_summary)
            zoneTrackingSummary.isEnabled = hasLocationPermission

            backgroundTracking = findViewById<SwitchCompat>(R.id.location_background).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    presenter.onToggleBackgroundTracking(isChecked)
                }
                isEnabled = hasLocationPermission
                isChecked = hasLocationPermission && isIgnoringBatteryOptimizations()
            }
            backgroundTrackingSummary = findViewById(R.id.location_background_summary)
            backgroundTrackingSummary.isEnabled = hasLocationPermission

            findViewById<AppCompatButton>(R.id.finish).setOnClickListener {
                (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
            }

            presenter.onRegistrationAttempt()
        }
    }

    override fun deviceRegistered() {
        viewFlipper.displayedChild = SETTINGS_VIEW
    }

    override fun showError() {
        viewFlipper.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        viewFlipper.displayedChild = LOADING_VIEW
    }

    override fun onDestroy() {
        PermissionManager.restartLocationTracking(context!!)
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
            zoneTracking.isEnabled = true
            zoneTrackingSummary.isEnabled = true
            zoneTracking.isChecked = true
            presenter.onToggleZoneTracking(true)

            backgroundTracking.isEnabled = true
            backgroundTrackingSummary.isEnabled = true
        } else {
            zoneTracking.isEnabled = false
            zoneTrackingSummary.isEnabled = false
            backgroundTracking.isEnabled = false
            backgroundTrackingSummary.isEnabled = false
        }

        requestBackgroundAccess()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == BACKGROUND_REQUEST && isIgnoringBatteryOptimizations()) {
            zoneTracking.isChecked = true
            presenter.onToggleBackgroundTracking(true)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        val intent: Intent
        if (!isIgnoringBatteryOptimizations()) {
            intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity?.packageName}")
            )
            startActivityForResult(intent, BACKGROUND_REQUEST)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
                context?.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(activity?.packageName ?: "")
                ?: false
    }
}
