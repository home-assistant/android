package io.homeassistant.companion.android.settings

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val view: SettingsView,
    private val authenticationUseCase: AuthenticationUseCase
) : SettingsPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun logout() {
        mainScope.launch {
            authenticationUseCase.revokeSession()
        }
        view.redirectToOnboarding()
    }

    override fun addNewInstance() {
        view.manageInstances()
    }
}