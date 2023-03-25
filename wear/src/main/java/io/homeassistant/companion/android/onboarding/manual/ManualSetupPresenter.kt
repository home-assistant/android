package io.homeassistant.companion.android.onboarding.manual

import android.content.Context

interface ManualSetupPresenter {

    fun onNextClicked(context: Context, url: String)

    fun onFinish()
}
