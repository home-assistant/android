package io.homeassistant.companion.android.onboarding.manual

import android.util.Log
import io.homeassistant.companion.android.domain.MalformedHttpUrlException
import io.homeassistant.companion.android.domain.url.UrlUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ManualSetupPresenterImpl @Inject constructor(
    private val view: ManualSetupView,
    private val urlUseCase: UrlUseCase
) : ManualSetupPresenter {

    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onClickOk(url: String) {
        mainScope.launch {
            try {
                urlUseCase.saveUrl(url, false)
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
