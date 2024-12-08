package io.shpro.companion.android.onboarding.discovery

import io.shpro.companion.android.common.data.HomeAssistantVersion
import java.net.URL

data class HomeAssistantInstance(
    val name: String,
    val url: URL,
    val version: HomeAssistantVersion
)
