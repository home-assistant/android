package io.homeassistant.companion.android.common.dagger

import android.content.Context
import io.homeassistant.companion.android.common.LocalStorageImpl
import kotlinx.coroutines.runBlocking

class Graph(
    private val context: Context
) {

    lateinit var appComponent: AppComponent
    private lateinit var dataComponent: DataComponent
    private lateinit var domainComponent: DomainComponent
    private var url = "http://localhost/"

    init {
        buildComponent()
        runBlocking {
            if (dataComponent.authenticationRepository().getUrl() != null) {
                this@Graph.url = dataComponent.authenticationRepository().getUrl()!!.toString()
            }
            buildComponent()
        }
    }

    fun urlUpdated() {
        runBlocking {
            this@Graph.url = dataComponent.authenticationRepository().getUrl()!!.toString()
        }
        buildComponent()
    }

    private fun buildComponent() {
        dataComponent = DaggerDataComponent
            .builder()
            .dataModule(
                DataModule(
                    url,
                    LocalStorageImpl(
                        context.getSharedPreferences(
                            "session",
                            Context.MODE_PRIVATE
                        )
                    ),
                    LocalStorageImpl(
                        context.getSharedPreferences(
                            "integration",
                            Context.MODE_PRIVATE
                        )
                    )
                )
            )
            .build()

        domainComponent = DaggerDomainComponent
            .builder()
            .dataComponent(dataComponent)
            .build()

        appComponent = DaggerAppComponent.builder()
            .domainComponent(domainComponent)
            .build()
    }
}
