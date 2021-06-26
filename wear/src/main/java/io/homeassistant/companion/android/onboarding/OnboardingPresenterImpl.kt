package io.homeassistant.companion.android.onboarding

import android.util.Log
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
import kotlinx.coroutines.*
import javax.inject.Inject

class OnboardingPresenterImpl @Inject constructor(
    private val view: OnboardingView,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
): OnboardingPresenter {
    companion object {
        private const val TAG = "OnboardingPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        TODO("Not yet implemented")
    }

    override fun onAdapterItemClick(instance: HomeAssistantInstance) {
        Log.d(TAG, "onAdapterItemClick: ${instance.name}")
        mainScope.launch {
            // Set current url
            urlUseCase.saveUrl(instance.url.toString())

            // Initiate login flow
            val flowInit: LoginFlowInit = authenticationUseCase.initiateLoginFlow()
            Log.d(TAG, "Created login flow step ${flowInit.stepId}: ${flowInit.flowId}")

            view.startAuthentication(flowInit.flowId)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}