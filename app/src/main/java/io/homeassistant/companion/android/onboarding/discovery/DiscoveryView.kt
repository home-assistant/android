package io.homeassistant.companion.android.onboarding.discovery

interface DiscoveryView {

    fun onUrlSaved()

    fun onInstanceFound(instance: HomeAssistantInstance)

    fun onScanError()
}
