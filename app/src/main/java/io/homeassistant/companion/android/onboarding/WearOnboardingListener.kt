package io.homeassistant.companion.android.onboarding

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.notifications.DaggerServiceComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class WearOnboardingListener : WearableListenerService() {

    @Inject
    lateinit var authenticationUseCase: AuthenticationRepository

    override fun onCreate() {
        super.onCreate()
        DaggerOnboardingListenerComponent.builder()
            .appComponent((applicationContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("WearOnboardingListener", "onMessageReceived: $event")

        if (event.path == "/request_authentication_token") {
            // Send authentication token
            val capabilityInfo: CapabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(this)
                    .getCapability("authentication_token", CapabilityClient.FILTER_REACHABLE)
            )
            sendAuthenticationToken(capabilityInfo)
        }
    }

    private fun sendAuthenticationToken(capabilityInfo: CapabilityInfo) = runBlocking {
        val nodeId: String? = pickBestNodeId(capabilityInfo.nodes)

        nodeId?.also { nodeId ->
            val session = authenticationUseCase.ensureValidSession(false)
            Log.d("WearOnboardingListener", "sendAuthenticationToken: access token: ${session.accessToken}")
            val putDataReq: PutDataRequest = PutDataMapRequest.create("/authentication_token"). run {
                dataMap.putString("access_token", session.accessToken)
                dataMap.putLong("expires_timestamp", session.expiresTimestamp)
                dataMap.putString("refresh_token", session.refreshToken)
                dataMap.putString("token_type", session.tokenType)
                setUrgent()
                asPutDataRequest()
            }
            val putDataTask: Task<DataItem> = Wearable.getDataClient(this@WearOnboardingListener).putDataItem(
                putDataReq.setUrgent()
            ).apply {
                addOnSuccessListener { Log.d("WearOnboardingListener", "sendAuthenticationToken: success") }
                addOnFailureListener { Log.d("WearOnboardingListener", "sendAuthenticationToken: failed") }
            }
        }
    }

    private fun pickBestNodeId(nodes: Set<Node>): String? {
        // Find a nearby node or pick one arbitrarily
        return nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
    }
}