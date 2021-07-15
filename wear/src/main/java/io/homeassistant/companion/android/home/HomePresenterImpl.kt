package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.*
import javax.inject.Inject

class HomePresenterImpl @Inject constructor(
    private val view: HomeView,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository
) : HomePresenter {

    companion object {
        const val TAG = "LaunchPresenter"
    }

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            view.showHomeAssistantVersion(integrationUseCase.getHomeAssistantVersion())
            view.showEntitiesCount(integrationUseCase.getEntities().size)
        }
    }

    override fun onLogoutClicked() {
        mainScope.launch {
            authenticationUseCase.revokeSession()
            view.displayLaunchView()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}