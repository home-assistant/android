package io.homeassistant.companion.android.onboarding.integration

interface MobileAppIntegrationPresenter {
    fun onRetry()
    fun onSkip()
    fun onFinish()
}