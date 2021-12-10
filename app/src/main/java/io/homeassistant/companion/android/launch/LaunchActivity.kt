package io.homeassistant.companion.android.launch

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.getMessagingToken
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class LaunchActivity : AppCompatActivity(), LaunchView {

    companion object {
        const val TAG = "LaunchActivity"
    }

    @Inject
    lateinit var presenter: LaunchPresenter

    @Inject
    lateinit var urlRepository: UrlRepository

    @Inject
    lateinit var authenticationRepository: AuthenticationRepository

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val registerActivityResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            this::onOnboardingComplete
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                MdcTheme {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
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
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    private fun onOnboardingComplete(result: ActivityResult) {
        mainScope.launch {
            val intent = result.data!!
            val url = intent.getStringExtra("URL").toString()
            val authCode = intent.getStringExtra("AuthCode").toString()
            val deviceName = intent.getStringExtra("DeviceName").toString()
            val deviceTrackingEnabled = intent.getBooleanExtra("LocationTracking", false)
            val messagingToken = getMessagingToken()
            if (messagingToken.isNullOrBlank()) {
                AlertDialog.Builder(this@LaunchActivity)
                    .setTitle(commonR.string.firebase_error_title)
                    .setMessage(commonR.string.firebase_error_message)
                    .setPositiveButton(commonR.string.skip) { _, _ ->
                        mainScope.launch {
                            registerAndOpenWebview(
                                url,
                                authCode,
                                deviceName,
                                messagingToken,
                                deviceTrackingEnabled
                            )
                        }
                    }
                    .show()
            } else {
                registerAndOpenWebview(
                    url,
                    authCode,
                    deviceName,
                    messagingToken,
                    deviceTrackingEnabled
                )
            }
        }
    }

    private suspend fun registerAndOpenWebview(
        url: String,
        authCode: String,
        deviceName: String,
        messagingToken: String,
        deviceTrackingEnabled: Boolean
    ) {
        urlRepository.saveUrl(url)
        authenticationRepository.registerAuthorizationCode(authCode)
        integrationRepository.registerDevice(
            DeviceRegistration(
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                deviceName,
                messagingToken
            )
        )
        setLocationTracking(deviceTrackingEnabled)
        displayWebview()
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
