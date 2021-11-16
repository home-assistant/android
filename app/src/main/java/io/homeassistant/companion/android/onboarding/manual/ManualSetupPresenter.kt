package io.homeassistant.companion.android.onboarding.manual

interface ManualSetupPresenter {
    fun init(manualSetupView: ManualSetupView)
    fun onClickOk(urlString: String)
    fun onFinish()
}
