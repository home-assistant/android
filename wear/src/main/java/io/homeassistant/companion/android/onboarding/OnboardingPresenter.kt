package io.homeassistant.companion.android.onboarding

interface OnboardingPresenter {

    fun onAdapterItemClick(instance: HomeAssistantInstance)

    fun onFinish()
}