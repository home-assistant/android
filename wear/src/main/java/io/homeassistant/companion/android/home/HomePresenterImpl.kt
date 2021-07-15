package io.homeassistant.companion.android.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import javax.inject.Inject

class HomePresenterImpl @Inject constructor(private val view: HomeView) : HomePresenter {

    companion object {
        const val TAG = "LaunchPresenter"
    }

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        TODO("Not yet implemented")
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}