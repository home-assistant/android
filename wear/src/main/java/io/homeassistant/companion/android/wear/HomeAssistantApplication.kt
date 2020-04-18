package io.homeassistant.companion.android.wear

import android.app.Application
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor

class HomeAssistantApplication : Application(), GraphComponentAccessor {

    private lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this, 0)
    }

    override val appComponent: AppComponent
        get() = graph.appComponent

}