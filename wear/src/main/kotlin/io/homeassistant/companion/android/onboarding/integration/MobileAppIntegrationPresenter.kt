package io.homeassistant.companion.android.onboarding.integration

import io.homeassistant.companion.android.database.server.TemporaryServer

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt(temporaryServer: TemporaryServer, deviceName: String)
    fun onFinish()
}
