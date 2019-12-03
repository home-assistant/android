package io.homeassistant.companion.android

import android.app.Application
import co.lokalise.android.sdk.LokaliseSDK
import com.jakewharton.threetenabp.AndroidThreeTen
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        LokaliseSDK.init("16ff9dee3da7a3cba0d998a4e58fa99e92ba089d", "145814835dd655bc5ab0d0.36753359", this)
        LokaliseSDK.updateTranslations()

        AndroidThreeTen.init(this)
        graph = Graph(this)
    }

    override val appComponent: AppComponent
        get() = graph.appComponent

    override fun urlUpdated() {
        graph.urlUpdated()
    }
}
