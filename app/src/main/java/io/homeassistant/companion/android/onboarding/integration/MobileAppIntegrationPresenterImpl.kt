package io.homeassistant.companion.android.onboarding.integration

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MobileAppIntegrationPresenterImpl @Inject constructor(
    private val view: MobileAppIntegrationView,
    private val integrationUseCase: IntegrationUseCase
) : MobileAppIntegrationPresenter {

    companion object {
        private const val TAG = "IntegrationPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRegistrationAttempt() {

        view.showLoading()

        mainScope.launch {

            var appData = hashMapOf(
                "push_url" to "https://mobile-apps.home-assistant.io/api/sendPushNotification"
            )

            FirebaseInstanceId.getInstance().instanceId
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "getInstanceId failed", task.exception)
                        return@OnCompleteListener
                    }

                    val token = task.result?.token.toString()

                    Log.w(TAG, "InstanceID Token:($token)")

                    appData.put("push_token", token)
                })

            val deviceRegistration = DeviceRegistration(
                BuildConfig.APPLICATION_ID,
                "Home Assistant",
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                Build.MODEL ?: "UNKNOWN",
                Build.MANUFACTURER ?: "UNKNOWN",
                Build.MODEL ?: "UNKNOWN",
                "Android",
                Build.VERSION.SDK_INT.toString(),
                false,
                appData
            )

            try {
                integrationUseCase.registerDevice(deviceRegistration)
                view.deviceRegistered()
            } catch (e: Exception) {
                Log.e(TAG, "Error with registering application", e)
                view.showError()
            }
        }
    }

    override fun onGrantedLocationPermission(context: Context, activity: Activity) {
        mainScope.launch {
            integrationUseCase.setZoneTrackingEnabled(true)
            integrationUseCase.setBackgroundTrackingEnabled(true)
            PermissionManager.restartLocationTracking(context, activity)
        }
    }

    override fun onSkip() {
        view.registrationSkipped()
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
