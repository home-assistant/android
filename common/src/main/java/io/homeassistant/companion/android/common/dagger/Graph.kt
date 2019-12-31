package io.homeassistant.companion.android.common.dagger

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.wifi.WifiHelperImpl
import kotlinx.coroutines.runBlocking

class Graph(
    private val application: Application
) {

    lateinit var appComponent: AppComponent
    private lateinit var dataComponent: DataComponent
    private lateinit var domainComponent: DomainComponent
    private var url = "http://localhost/"

    init {
        buildComponent()
        urlUpdated()
    }

    fun urlUpdated() {
        runBlocking {
            if (dataComponent.urlRepository().getUrl() != null) {
                this@Graph.url = dataComponent.urlRepository().getUrl().toString()
                buildComponent()
            }
        }
    }

    private fun buildComponent() {
        dataComponent = DaggerDataComponent
            .builder()
            .dataModule(
                DataModule(
                    url,
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "url",
                            Context.MODE_PRIVATE
                        )
                    ),
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "session",
                            Context.MODE_PRIVATE
                        )
                    ),
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "integration",
                            Context.MODE_PRIVATE
                        )
                    ),
                    WifiHelperImpl(application.getSystemService(Context.WIFI_SERVICE) as WifiManager)
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
