package io.homeassistant.companion.android.onboarding.manual

interface ManualSetupPresenter {

    fun onClickOk(urlString: String)

    fun onFinish()
}
