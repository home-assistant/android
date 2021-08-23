package io.homeassistant.companion.android.onboarding.manual_setup

import android.util.Log
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class ManualSetupPresenterImpl @Inject constructor(
    private val view: ManualSetupView,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
) : ManualSetupPresenter {
    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onNextClicked(url: String) {
        view.showLoading()
        mainScope.launch {
            // Set current url
            urlUseCase.saveUrl(url)

            // Initiate login flow
            try {
                val flowInit: LoginFlowInit = authenticationUseCase.initiateLoginFlow()
                Log.d(TAG, "Created login flow step ${flowInit.stepId}: ${flowInit.flowId}")

                view.startAuthentication(flowInit.flowId)
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
