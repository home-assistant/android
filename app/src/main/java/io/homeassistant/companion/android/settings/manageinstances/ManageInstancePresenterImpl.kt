package io.homeassistant.companion.android.settings.manageinstances

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import kotlinx.coroutines.*
import javax.inject.Inject

class ManageInstancePresenterImpl @Inject constructor(
    private val view: ManageInstanceView,
    private val authenticationUseCase: AuthenticationUseCase
) : ManageInstancePresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var instanceList: List<String>? = null

    override fun getInstances() {
        mainScope.launch {
            instanceList = withContext(Dispatchers.IO) {
                authenticationUseCase.getAllInstanceUrls()
            }
            instanceList?.let {
                view.showInstanceList(it)
            }
        }
    }

    override fun switchToInstance(url: String) {
        mainScope.launch { authenticationUseCase.setCurrentInstance(url) }
        view.launchInstance()
    }

    override fun addNewInstance() {
        view.addNewInstance()
    }

    override fun deleteInstance(url: String) {
        mainScope.launch { authenticationUseCase.deleteInstance(url) }
        getInstances()
    }
}