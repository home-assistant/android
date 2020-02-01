package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val FIRE_EVENT = "FIRE_EVENT"
        const val EXTRA_ACTION_KEY = "EXTRA_ACTION_KEY"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context?, intent: Intent?) {
        DaggerServiceComponent.builder()
            .appComponent((context!!.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val actionKey = intent?.getStringExtra(EXTRA_ACTION_KEY) ?: "ANDROID_ACTION_UNKNOWN"

        ioScope.launch {
            integrationUseCase.fireEvent(actionKey, mapOf())
        }

    }

}