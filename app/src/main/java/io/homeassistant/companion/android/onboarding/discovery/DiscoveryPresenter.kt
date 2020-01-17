package io.homeassistant.companion.android.onboarding.discovery

import java.net.URL

interface DiscoveryPresenter {
    fun onUrlSelected(url: URL)
    fun onFinish()
}
