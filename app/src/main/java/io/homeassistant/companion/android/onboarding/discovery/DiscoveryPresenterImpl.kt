package io.homeassistant.companion.android.onboarding.discovery

import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

class DiscoveryPresenterImpl @Inject constructor(
    val view: DiscoveryView,
    val urlUseCase: UrlRepository
) : DiscoveryPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUrlSelected(url: URL) {
        mainScope.launch {
            urlUseCase.saveUrl(url.toString())
            view.onUrlSaved()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
