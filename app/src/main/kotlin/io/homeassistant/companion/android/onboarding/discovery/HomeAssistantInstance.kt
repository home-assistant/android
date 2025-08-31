package io.homeassistant.companion.android.onboarding.discovery

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import java.net.URL

data class HomeAssistantInstance(val name: String, val url: URL, val version: HomeAssistantVersion)
