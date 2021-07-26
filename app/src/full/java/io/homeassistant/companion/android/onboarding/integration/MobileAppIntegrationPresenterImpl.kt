package io.homeassistant.companion.android.onboarding.integration

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.iid.FirebaseInstanceId
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
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
                val instanceId = Tasks.await(FirebaseInstanceId.getInstance().instanceId)
                registration.pushToken = instanceId.token
            } catch (e: Exception) {
                Log.e(TAG, "Unable to get firebase token.", e)
                throw e
            }
        }

        return registration
    }
}
