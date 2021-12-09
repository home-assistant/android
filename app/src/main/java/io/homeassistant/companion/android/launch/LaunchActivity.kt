package io.homeassistant.companion.android.launch

import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.getRegistrationCode
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LaunchActivity : BaseActivity(), LaunchView {

    companion object {
        const val TAG = "LaunchActivity"

        private const val BACKGROUND_REQUEST = 99
        private const val LOCATION_REQUEST_CODE = 0
    }

    @Inject
    lateinit var presenter: LaunchPresenter

    @Inject
    lateinit var urlRepository: UrlRepository

    @Inject
    lateinit var authenticationRepository: AuthenticationRepository

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private var dialog: AlertDialog? = null

    private val registerActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            ioScope.launch {
                Log.i(TAG, "Got result from registration: $it")
                it?.data?.let { intent ->
                    // Get data out, store it, open webview
                    urlRepository.saveUrl(intent.getStringExtra("URL").toString())
                    authenticationRepository.registerAuthorizationCode(
                        intent.getStringExtra("AuthCode").toString()
                    )
                    integrationRepository.registerDevice(
                        DeviceRegistration(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            intent.getStringExtra("DeviceName").toString(),
                            getRegistrationCode()
                        )
                    )
                    setLocationTracking(intent.getBooleanExtra("LocationTracking", false))
                    displayWebview()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.onViewReady()
    }

    override fun displayWebview() {
        presenter.setSessionExpireMillis(0)

        startActivity(WebViewActivity.newInstance(this, intent.data?.path))
        finish()
        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }

    override fun displayOnBoarding(sessionConnected: Boolean) {
        val intent = OnboardingActivity.newInstance(this)
        registerActivityResult.launch(intent)
//        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    private fun setLocationTracking(enabled: Boolean) {
        val sensorDao = AppDatabase.getInstance(applicationContext).sensorDao()
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
}
