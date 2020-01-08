package io.homeassistant.companion.android

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.lokalise.sdk.Lokalise
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        Lokalise.init(this, "16ff9dee3da7a3cba0d998a4e58fa99e92ba089d", "145814835dd655bc5ab0d0.36753359")
        Lokalise.updateTranslations()

        AndroidThreeTen.init(this)
        graph = Graph(this, 0)
    }

    override val appComponent: AppComponent
        get() = graph.appComponent

    override fun urlUpdated() {
        graph.urlUpdated()
    }
}
