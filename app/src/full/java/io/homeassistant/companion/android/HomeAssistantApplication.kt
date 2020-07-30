package io.homeassistant.companion.android

import com.lokalise.sdk.Lokalise
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

class HomeAssistantApplication : MinimalHomeAssistantApplication(), GraphComponentAccessor {

    override fun onCreate() {
        super.onCreate()

        Lokalise.init(
            this,
            "16ff9dee3da7a3cba0d998a4e58fa99e92ba089d",
            "145814835dd655bc5ab0d0.36753359"
        )
        Lokalise.updateTranslations()
    }
}
