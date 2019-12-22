package io.homeassistant.companion.android.onboarding.integration

import android.app.Activity
import android.content.Context

interface MobileAppIntegrationPresenter {
    fun onRegistrationAttempt()
    fun onGrantedLocationPermission(context: Context, activity: Activity)
    fun onFinish()
}
