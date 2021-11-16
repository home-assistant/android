package io.homeassistant.companion.android.onboarding.discovery

import java.net.URL

interface DiscoveryPresenter {
    fun init(discoveryView: DiscoveryView)
    fun onUrlSelected(url: URL)
    fun onFinish()
}
