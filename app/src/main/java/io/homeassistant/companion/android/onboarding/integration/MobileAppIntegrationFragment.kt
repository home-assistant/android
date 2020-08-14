package io.homeassistant.companion.android.onboarding.integration

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.sensors.LocationBroadcastReceiver
import io.homeassistant.companion.android.sensors.PhoneStateSensorManager
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_mobile_app_integration.*

class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        private const val LOADING_VIEW = 0
        private const val ERROR_VIEW = 1
        private const val SETTINGS_VIEW = 2

        private const val BACKGROUND_REQUEST = 99

        private const val LOCATION_REQUEST_CODE = 0
        private const val PHONE_REQUEST_CODE = 1

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
    private lateinit var callTracking: SwitchCompat
    private lateinit var callTrackingSummary: AppCompatTextView

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
                presenter.onRegistrationAttempt(false)
            }

            findViewById<AppCompatButton>(R.id.location_perms).apply {
                setOnClickListener {
                    this@MobileAppIntegrationFragment.requestPermissions(
                        LocationBroadcastReceiver().requiredPermissions(),
                        LOCATION_REQUEST_CODE
                    )
                }
            }

            val hasLocationPermission = LocationBroadcastReceiver().checkPermission(context)

            zoneTracking = findViewById<SwitchCompat>(R.id.location_zone).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    updateSensorDao(LocationBroadcastReceiver.zoneLocation.id, isChecked)
                }
                isEnabled = hasLocationPermission
                isChecked = hasLocationPermission
            }
            zoneTrackingSummary = findViewById(R.id.location_zone_summary)
            zoneTrackingSummary.isEnabled = hasLocationPermission

            backgroundTracking = findViewById<SwitchCompat>(R.id.location_background).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    updateSensorDao(LocationBroadcastReceiver.backgroundLocation.id, isChecked)
                }
                isEnabled = hasLocationPermission
                isChecked = hasLocationPermission && isIgnoringBatteryOptimizations()
            }
            backgroundTrackingSummary = findViewById(R.id.location_background_summary)
            backgroundTrackingSummary.isEnabled = hasLocationPermission

            // Calls tracking
            findViewById<AppCompatButton>(R.id.phone_state_perms).apply {
                setOnClickListener {
                    this@MobileAppIntegrationFragment.requestPermissions(PhoneStateSensorManager().requiredPermissions(), PHONE_REQUEST_CODE)
                }
            }

            val hasPhoneStatePermission = PhoneStateSensorManager().checkPermission(requireContext())

            callTracking = findViewById<SwitchCompat>(R.id.call_tracking).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    updateSensorDao(PhoneStateSensorManager.phoneState.id, isChecked)
                }
                isEnabled = hasPhoneStatePermission
                isChecked = hasPhoneStatePermission
            }

            callTrackingSummary = findViewById(R.id.call_tracking_summary)
            callTrackingSummary.isEnabled = hasPhoneStatePermission

            findViewById<AppCompatButton>(R.id.finish).setOnClickListener {
                (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
            }

            findViewById<AppCompatButton>(R.id.skip).setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(R.string.firebase_error_title)
                    .setMessage(R.string.firebase_error_message)
                    .setPositiveButton(R.string.skip) { _, _ ->
                        presenter.onRegistrationAttempt(true)
                    }
                    .setNegativeButton(R.string.retry) { _, _ ->
                        presenter.onRegistrationAttempt(false)
                    }
                    .create()
                    .show()
            }

            presenter.onRegistrationAttempt(false)
        }
    }

    override fun deviceRegistered() {
        viewFlipper.displayedChild = SETTINGS_VIEW
    }

    override fun showError(skippable: Boolean) {
        if (skippable) {
            skip.visibility = View.VISIBLE
        }
        viewFlipper.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        viewFlipper.displayedChild = LOADING_VIEW
    }

    override fun onDestroy() {
        LocationBroadcastReceiver.restartLocationTracking(requireContext())
        presenter.onFinish()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                zoneTracking.isEnabled = true
                zoneTrackingSummary.isEnabled = true
                zoneTracking.isChecked = true
                updateSensorDao(LocationBroadcastReceiver.zoneLocation.id, true)

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

        if (requestCode == PHONE_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                callTracking.isEnabled = true
                callTracking.isChecked = true
                callTrackingSummary.isEnabled = true
                updateSensorDao(PhoneStateSensorManager.phoneState.id, true)
            } else {
                callTracking.isEnabled = false
                callTrackingSummary.isEnabled = false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == BACKGROUND_REQUEST && isIgnoringBatteryOptimizations()) {
            zoneTracking.isChecked = true
            updateSensorDao(LocationBroadcastReceiver.backgroundLocation.id, true)
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

    private fun updateSensorDao(uniqueId: String, isChecked: Boolean) {
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        var sensor = sensorDao.get(uniqueId)
        if (sensor == null) {
            sensor = Sensor(
                uniqueId,
                isChecked,
                false,
                ""
            )
            sensorDao.add(sensor)
        } else {
            sensor.enabled = isChecked
            sensorDao.update(sensor)
        }
    }
}
