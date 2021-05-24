package io.homeassistant.companion.android.onboarding.integration

import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    view: MobileAppIntegrationView,
    integrationUseCase: IntegrationRepository
) : MobileAppIntegrationPresenterBase(
    view, integrationUseCase
) {
    override suspend fun createRegistration(simple: Boolean, deviceName: String): DeviceRegistration {
        val registration = super.createRegistration(simple, deviceName)

        if (!simple) {
            try {
                val instanceId = FirebaseInstanceId.getInstance().instanceId.await()
                registration.pushToken = instanceId.token
            } catch (e: Exception) {
                Log.e(TAG, "Unable to get firebase token.", e)
                throw e
            }
        }

        return registration
    }
}
