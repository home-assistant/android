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
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_mobile_app_integration.*

class MobileAppIntegrationFragment : Fragment(), MobileAppIntegrationView {

    companion object {
        private const val LOADING_VIEW = 1
        private const val ERROR_VIEW = 2

        private const val BACKGROUND_REQUEST = 99

        private const val LOCATION_REQUEST_CODE = 0

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

            findViewById<TextInputEditText>(R.id.deviceName).setText(Build.MODEL)

            findViewById<SwitchMaterial>(R.id.locationTracking)?.let {
                val sensorId = LocationSensorManager.backgroundLocation.id
                it.isChecked = LocationSensorManager().checkPermission(context, sensorId)
                it.setOnCheckedChangeListener { _, isChecked ->
                    setLocationTracking(isChecked)
                    if (isChecked && !LocationSensorManager().checkPermission(requireContext(), sensorId)) {
                        this@MobileAppIntegrationFragment.requestPermissions(
                            LocationSensorManager().requiredPermissions(sensorId),
                            LOCATION_REQUEST_CODE
                        )
                    }
                }
            }

            findViewById<AppCompatButton>(R.id.finish).setOnClickListener {
                presenter.onRegistrationAttempt(false, deviceName.text.toString())
            }

            findViewById<AppCompatButton>(R.id.retry).setOnClickListener {
                presenter.onRegistrationAttempt(false, deviceName.text.toString())
            }
        }
    }

    override fun deviceRegistered() {
        (activity as MobileAppIntegrationListener).onIntegrationRegistrationComplete()
    }

    override fun showWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.firebase_error_title)
            .setMessage(R.string.firebase_error_message)
            .setPositiveButton(R.string.skip) { _, _ ->
                presenter.onRegistrationAttempt(true, deviceName.text.toString())
            }
            .setNegativeButton(R.string.retry) { _, _ ->
                presenter.onRegistrationAttempt(false, deviceName.text.toString())
            }
            .show()
    }

    override fun showError() {
        viewFlipper.displayedChild = ERROR_VIEW
    }

    override fun showLoading() {
        viewFlipper.displayedChild = LOADING_VIEW
    }

    override fun onDestroy() {
        SensorWorker.start(requireContext())
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
            val hasPermission = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            locationTracking.isChecked = hasPermission
            setLocationTracking(hasPermission)
            requestBackgroundAccess()
        }
    }

    private fun setLocationTracking(enabled: Boolean) {
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        arrayOf(
            LocationSensorManager.backgroundLocation,
            LocationSensorManager.zoneLocation,
            LocationSensorManager.singleAccurateLocation
        ).forEach { basicSensor ->
            var sensorEntity = sensorDao.get(basicSensor.id)
            if (sensorEntity != null) {
                sensorEntity.enabled = enabled
                sensorEntity.lastSentState = ""
                sensorDao.update(sensorEntity)
            } else {
                sensorEntity = Sensor(basicSensor.id, enabled, false, "")
                sensorDao.add(sensorEntity)
            }
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
