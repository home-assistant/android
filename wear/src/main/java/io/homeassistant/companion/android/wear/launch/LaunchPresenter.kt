package io.homeassistant.companion.android.wear.launch

import com.google.android.gms.wearable.MessageClient

interface LaunchPresenter : MessageClient.OnMessageReceivedListener {
    fun onViewReady()
    suspend fun onRefresh()
    fun onFinish()
}