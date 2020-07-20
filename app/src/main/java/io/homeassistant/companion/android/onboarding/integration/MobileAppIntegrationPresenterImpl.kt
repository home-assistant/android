package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
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

    override fun onRegistrationAttempt(includeFirebase: Boolean) {
        view.showLoading()

        val deviceRegistration = DeviceRegistration(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            Build.MODEL ?: "UNKNOWN"
        )

        if (includeFirebase) {
            val instanceId = FirebaseInstanceId.getInstance().instanceId
            instanceId.addOnSuccessListener {
                deviceRegistration.pushToken = it.token
                register(deviceRegistration)
            }
            instanceId.addOnFailureListener {
                Log.e(TAG, "Couldn't get FirebaseInstanceId", it)
                view.showError(true)
            }
        } else {
            register(deviceRegistration)
        }
    }

    private fun register(deviceRegistration: DeviceRegistration) {
        mainScope.launch {
            try {
                integrationUseCase.registerDevice(deviceRegistration)
                // TODO: Get the name of the instance to display
                view.deviceRegistered()
            } catch (e: Exception) {
                Log.e(TAG, "Error with registering application", e)
                view.showError()
            }
        }
    }

    override fun onToggleZoneTracking(enabled: Boolean) {
        mainScope.launch {
            integrationUseCase.setZoneTrackingEnabled(enabled)
        }
    }

    override fun onToggleBackgroundTracking(enabled: Boolean) {
        mainScope.launch {
            integrationUseCase.setBackgroundTrackingEnabled(enabled)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
