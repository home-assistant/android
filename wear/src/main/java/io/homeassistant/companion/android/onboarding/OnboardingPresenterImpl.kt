package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowForm
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

class OnboardingPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
) : OnboardingPresenter {
    companion object {
        private const val TAG = "OnboardingPresenter"
    }

    private val view = context as OnboardingView

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: [${dataEvents.count}]")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        Log.d(TAG, "onDataChanged: found home_assistant_instance")
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceFound(instance)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_assistant_instance") == 0) {
                        val instance = getInstance(DataMapItem.fromDataItem(item).dataMap)
                        view.onInstanceLost(instance)
                    }
                }
            }
        }
        dataEvents.release()
    }

    override fun getInstance(map: DataMap): HomeAssistantInstance {
        map.apply {
            return HomeAssistantInstance(
                getString("name", ""),
                URL(getString("url", "")),
                getString("version", "")
            )
        }
    }

    override fun onAdapterItemClick(instance: HomeAssistantInstance) {
        Log.d(TAG, "onAdapterItemClick: ${instance.name}")
        view.showLoading()
        mainScope.launch {
            // Set current url
            urlUseCase.saveUrl(instance.url.toString())

            // Initiate login flow
            try {
                val flowForm: LoginFlowForm = authenticationUseCase.initiateLoginFlow()
                Log.d(TAG, "Created login flow step ${flowForm.stepId}: ${flowForm.flowId}")

                view.startAuthentication(flowForm.flowId)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to initiate login flow", e)
                view.showError()
            }
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
