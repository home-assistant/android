package io.homeassistant.companion.android.lock

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LockPresenterImpl @Inject constructor(
    private val view: LockView,
    private val authenticationUseCase: AuthenticationUseCase
) : LockPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun isLockEnabled(): Boolean {
        return runBlocking {
            authenticationUseCase.isLockEnabled()
        }
    }

    override fun onViewReady() {
        mainScope.launch {
                view.displayWebview()
        }
    }
}
