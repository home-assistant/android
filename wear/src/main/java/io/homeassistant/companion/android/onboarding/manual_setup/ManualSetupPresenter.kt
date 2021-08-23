package io.homeassistant.companion.android.onboarding.manual_setup

interface ManualSetupPresenter {

    fun onNextClicked(url: String)

    fun onFinish()
}
