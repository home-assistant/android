package io.homeassistant.companion.android.common.dagger

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.wifi.WifiHelperImpl

class Graph(
    private val application: Application,
    private val instanceId: Int
) {

    lateinit var appComponent: AppComponent
    private lateinit var dataComponent: DataComponent
    private lateinit var domainComponent: DomainComponent

    init {
        buildComponent()
    }

    @SuppressLint("HardwareIds")
    private fun buildComponent() {
        dataComponent = DaggerDataComponent
            .builder()
            .dataModule(
                DataModule(
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "url_$instanceId",
                            Context.MODE_PRIVATE
                        )
                    ),
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "session_$instanceId",
                            Context.MODE_PRIVATE
                        )
                    ),
                    LocalStorageImpl(
                        application.getSharedPreferences(
                            "integration_$instanceId",
                            Context.MODE_PRIVATE
                        )
                    ),
                    WifiHelperImpl(application.getSystemService(Context.WIFI_SERVICE) as WifiManager),
                    Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
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
