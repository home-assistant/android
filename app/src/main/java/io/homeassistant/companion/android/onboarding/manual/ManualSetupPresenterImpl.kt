package io.homeassistant.companion.android.onboarding.manual

import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import kotlinx.coroutines.*
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

class ManualSetupPresenterImpl @Inject constructor(
    private val view: ManualSetupView,
    private val authenticationUseCase: AuthenticationUseCase
) : ManualSetupPresenter {

    companion object {
        private const val TAG = "ManualSetupPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onClickOk(urlString: String) {
        val url: URL
        try {
            url = URL(urlString)
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Unable to parse url", e)
            if(e.message != null){
                val message: String = e.message as String
                if(message.contains("protocol")){
                    view.displayUrlError(URLError.NO_PROTOCOL)
                } else {
                    view.displayUrlError(e.localizedMessage)
                }
            }
            return
        }

        mainScope.launch {
            authenticationUseCase.saveUrl(url)
            view.urlSaved()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

}