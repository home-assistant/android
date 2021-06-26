package io.homeassistant.companion.android.onboarding

interface OnboardingPresenter {

    fun onViewReady()

    fun onAdapterItemClick(instance: HomeAssistantInstance)

    fun onFinish()
}