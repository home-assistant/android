package io.homeassistant.companion.android.onboarding

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.notifications.DaggerServiceComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class WearOnboardingListener : WearableListenerService() {

    @Inject
    lateinit var authenticationUseCase: AuthenticationRepository

    @Inject
    lateinit var urlUseCase: UrlRepository

    override fun onCreate() {
        super.onCreate()
        DaggerOnboardingListenerComponent.builder()
            .appComponent((applicationContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("WearOnboardingListener", "onMessageReceived: $event")

        if (event.path == "/request_home_assistant_instance") {
            val nodeId = event.sourceNodeId
            sendHomeAssistantInstance(nodeId)
        }
    }

    private fun sendHomeAssistantInstance(nodeId: String) = runBlocking {
        Log.d("WearOnboardingListener", "sendHomeAssistantInstance: $nodeId")
        /*
        TODO move database.authentication to common
        This also means that database.AppDatabase needs to be split up
        Then try to retrieve an Authentication instance from the database (will only be present if the user selected "remember me")
        If it is present, use that to get the url, hostname, username and password
        If it is not present, use the current method and add empty username and password
         */
        // Retrieve current instance
        val url = urlUseCase.getUrl()

        // Put as DataMap in data layer
        val putDataReq: PutDataRequest = PutDataMapRequest.create("/home_assistant_instance").run {
            dataMap.putString("name", url?.host.toString())
            dataMap.putString("url", url.toString())
            setUrgent()
            asPutDataRequest()
        }
        Wearable.getDataClient(this@WearOnboardingListener).putDataItem(putDataReq).apply {
            addOnSuccessListener { Log.d("WearOnboardingListener", "sendHomeAssistantInstance: success") }
            addOnFailureListener { Log.d("WearOnboardingListener", "sendHomeAssistantInstance: failed") }
        }
    }
}