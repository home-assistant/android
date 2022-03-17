package io.homeassistant.companion.android.onboarding.manual_setup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowForm
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class ManualSetupPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val authenticationUseCase: AuthenticationRepository,
    private val urlUseCase: UrlRepository
) : ManualSetupPresenter {
    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val view = context as ManualSetupView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onNextClicked(url: String) {
        view.showLoading()
        mainScope.launch {
            // Set current url
            urlUseCase.saveUrl(url)

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
