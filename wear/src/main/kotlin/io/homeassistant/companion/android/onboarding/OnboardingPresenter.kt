package io.homeassistant.companion.android.onboarding

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap

interface OnboardingPresenter : DataClient.OnDataChangedListener {

    fun onInstanceClickedWithoutApp(context: Context, url: String)

    fun onFinish()

    fun getInstance(map: DataMap): HomeAssistantInstance
}
