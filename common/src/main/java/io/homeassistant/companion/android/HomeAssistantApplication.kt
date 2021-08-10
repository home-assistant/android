package io.homeassistant.companion.android

import android.app.Application
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

open class HomeAssistantApplication : Application(), GraphComponentAccessor {
    lateinit var graph: Graph
    protected val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    override val appComponent: AppComponent
        get() = graph.appComponent
}