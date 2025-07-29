package io.homeassistant.companion.android.launch

import kotlinx.coroutines.CoroutineScope

interface LaunchPresenter {

    fun onViewReady(serverUrlToOnboard: String? = null, coroutineScope: CoroutineScope)

    suspend fun setSessionExpireMillis(value: Long)

    fun hasMultipleServers(): Boolean
}
