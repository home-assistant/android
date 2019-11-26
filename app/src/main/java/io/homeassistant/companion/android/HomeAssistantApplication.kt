package io.homeassistant.companion.android

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        AndroidThreeTen.init(this)
        graph = Graph(this)
    }

    override val appComponent: AppComponent
        get() = graph.appComponent

    override fun urlUpdated() {
        graph.urlUpdated()
    }
}
