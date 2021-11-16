package io.homeassistant.companion.android.onboarding.manual

import android.util.Log
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class ManualSetupPresenterImpl @Inject constructor(
    private val urlUseCase: UrlRepository
) : ManualSetupPresenter {

    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var view: ManualSetupView

    override fun init(manualSetupView: ManualSetupView) {
        view = manualSetupView
    }

    override fun onClickOk(urlString: String) {
        mainScope.launch {
            try {
                urlUseCase.saveUrl(urlString, false)
            } catch (e: MalformedHttpUrlException) {
                Log.e(TAG, "Unable to parse url", e)
                view.displayUrlError()
                return@launch
            }
            view.urlSaved()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
