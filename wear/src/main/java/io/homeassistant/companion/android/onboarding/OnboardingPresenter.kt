package io.homeassistant.companion.android.onboarding

import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap

interface OnboardingPresenter : DataClient.OnDataChangedListener {

    fun onAdapterItemClick(instance: HomeAssistantInstance)

    fun onFinish()

    fun getInstance(map: DataMap): HomeAssistantInstance
}
