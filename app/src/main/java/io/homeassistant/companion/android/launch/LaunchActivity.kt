package io.homeassistant.companion.android.launch

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.onboarding.OnboardApp
import io.homeassistant.companion.android.onboarding.getMessagingToken
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.settings.SettingViewModel
import io.homeassistant.companion.android.settings.server.ServerChooserFragment
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class LaunchActivity : AppCompatActivity(), LaunchView {

    companion object {
        const val TAG = "LaunchActivity"
    }

    @Inject
    lateinit var presenter: LaunchPresenter

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var sensorDao: SensorDao

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val settingViewModel: SettingViewModel by viewModels()

    private val registerActivityResult = registerForActivityResult(
        OnboardApp(),
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

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) && BuildConfig.FLAVOR == "full") {
            val carIntent = Intent(
                this,
                Class.forName("androidx.car.app.activity.CarAppActivity")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(carIntent)
        } else if (presenter.hasMultipleServers() && intent.data?.path?.isNotBlank() == true) {
            supportFragmentManager.setFragmentResultListener(ServerChooserFragment.RESULT_KEY, this) { _, bundle ->
                val serverId = if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                    bundle.getInt(ServerChooserFragment.RESULT_SERVER)
                } else {
                    null
                }
                supportFragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
                startActivity(WebViewActivity.newInstance(this, intent.data?.path, serverId))
                finish()
                overridePendingTransition(0, 0) // Disable activity start/stop animation
            }
            ServerChooserFragment().show(supportFragmentManager, ServerChooserFragment.TAG)
            return
        } else {
            startActivity(WebViewActivity.newInstance(this, intent.data?.path))
        }
        finish()
        overridePendingTransition(0, 0) // Disable activity start/stop animation
    }

    override fun displayOnBoarding(sessionConnected: Boolean) {
        registerActivityResult.launch(OnboardApp.Input())
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    private fun onOnboardingComplete(result: OnboardApp.Output?) {
        mainScope.launch {
            if (result != null) {
                val (url, authCode, deviceName, deviceTrackingEnabled, notificationsEnabled) = result
                val messagingToken = getMessagingToken()
                if (messagingToken.isBlank() && BuildConfig.FLAVOR == "full") {
                    AlertDialog.Builder(this@LaunchActivity)
                        .setTitle(commonR.string.firebase_error_title)
                        .setMessage(commonR.string.firebase_error_message)
                        .setPositiveButton(commonR.string.continue_connect) { _, _ ->
                            mainScope.launch {
                                registerAndOpenWebview(
                                    url,
                                    authCode,
                                    deviceName,
                                    messagingToken,
                                    deviceTrackingEnabled,
                                    notificationsEnabled
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
                        deviceTrackingEnabled,
                        notificationsEnabled
                    )
                }
            } else {
                Log.e(TAG, "onOnboardingComplete: Activity result returned null intent data")
            }
        }
    }

    private suspend fun registerAndOpenWebview(
        url: String,
        authCode: String,
        deviceName: String,
        messagingToken: String,
        deviceTrackingEnabled: Boolean,
        notificationsEnabled: Boolean
    ) {
        var serverId: Int? = null
        try {
            val formattedUrl = UrlUtil.formattedUrlString(url)
            val server = Server(
                _name = "",
                type = ServerType.TEMPORARY,
                connection = ServerConnectionInfo(
                    externalUrl = formattedUrl
                ),
                session = ServerSessionInfo(),
                user = ServerUserInfo()
            )
            serverId = serverManager.addServer(server)
            serverManager.authenticationRepository(serverId).registerAuthorizationCode(authCode)
            serverManager.integrationRepository(serverId).registerDevice(
                DeviceRegistration(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    deviceName,
                    messagingToken
                )
            )
            serverId = serverManager.convertTemporaryServer(serverId)
        } catch (e: Exception) {
            // Fatal errors: if one of these calls fail, the app cannot proceed.
            // Show an error, clean up the session and require new registration.
            // Because this runs after the webview, the only expected errors are:
            // - missing mobile_app integration
            // - system version related in OkHttp (cryptography)
            // - general connection issues (offline/unknown)
            Log.e(TAG, "Exception while registering", e)
            try {
                if (serverId != null) {
                    serverManager.authenticationRepository(serverId).revokeSession()
                    serverManager.removeServer(serverId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can't revoke session", e)
            }
            AlertDialog.Builder(this@LaunchActivity)
                .setTitle(commonR.string.error_connection_failed)
                .setMessage(
                    when {
                        e is HttpException && e.code() == 404 -> commonR.string.error_with_registration
                        e is SSLHandshakeException -> commonR.string.webview_error_FAILED_SSL_HANDSHAKE
                        e is SSLException -> commonR.string.webview_error_SSL_INVALID
                        else -> commonR.string.webview_error
                    }
                )
                .setCancelable(false)
                .setPositiveButton(commonR.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    displayOnBoarding(false)
                }
                .show()
            return
        }
        serverId?.let {
            setLocationTracking(serverId, deviceTrackingEnabled)
            setNotifications(serverId, notificationsEnabled)
        }
        displayWebview()
    }

    private suspend fun setLocationTracking(serverId: Int, enabled: Boolean) {
        sensorDao.setSensorsEnabled(
            sensorIds = listOf(
                LocationSensorManager.backgroundLocation.id,
                LocationSensorManager.zoneLocation.id,
                LocationSensorManager.singleAccurateLocation.id
            ),
            serverId = serverId,
            enabled = enabled
        )
    }

    private fun setNotifications(serverId: Int, enabled: Boolean) {
        // Full: this only refers to the system permission on Android 13+ so no changes are necessary.
        // Minimal: change persistent connection setting to reflect preference.
        if (BuildConfig.FLAVOR != "full") {
            settingViewModel.getSetting(serverId) // Required to create initial value
            settingViewModel.updateWebsocketSetting(
                serverId,
                if (enabled) WebsocketSetting.ALWAYS else WebsocketSetting.NEVER
            )
        }
    }
}
