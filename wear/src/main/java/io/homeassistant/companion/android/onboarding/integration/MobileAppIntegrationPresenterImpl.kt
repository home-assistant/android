package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.onboarding.getMessagingToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val integrationUseCase: IntegrationRepository
) : MobileAppIntegrationPresenter {

    companion object {
        internal const val TAG = "IntegrationPresenter"
    }

    private val view = context as MobileAppIntegrationView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private suspend fun createRegistration(deviceName: String): DeviceRegistration {
        return DeviceRegistration(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            deviceName,
            getMessagingToken(),
            false
        )
    }

    override fun onRegistrationAttempt(deviceName: String) {
        view.showLoading()
        mainScope.launch {
            val deviceRegistration = createRegistration(deviceName)
            try {
                integrationUseCase.registerDevice(deviceRegistration)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register with Home Assistant", e)
                view.showError()
                return@launch
            }
            view.deviceRegistered()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
