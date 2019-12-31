package io.homeassistant.companion.android.common.dagger

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.data.HomeAssistantRetrofit
import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.data.authentication.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.data.authentication.AuthenticationService
import io.homeassistant.companion.android.data.integration.IntegrationRepositoryImpl
import io.homeassistant.companion.android.data.integration.IntegrationService
import io.homeassistant.companion.android.data.url.UrlRepositoryImpl
import io.homeassistant.companion.android.data.wifi.WifiHelper
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.url.UrlRepository
import javax.inject.Named

@Module(includes = [DataModule.Declaration::class])
class DataModule(
    private val url: String,
    private val urlStorage: LocalStorage,
    private val sessionLocalStorage: LocalStorage,
    private val integrationLocalStorage: LocalStorage,
    private val wifiHelper: WifiHelper
) {

    @Provides
    fun provideEndPoint() = url

    @Provides
    fun provideAuthenticationService(homeAssistantRetrofit: HomeAssistantRetrofit) =
        homeAssistantRetrofit.retrofit.create(AuthenticationService::class.java)

    @Provides
    fun providesIntegrationService(homeAssistantRetrofit: HomeAssistantRetrofit) =
        homeAssistantRetrofit.retrofit.create(IntegrationService::class.java)

    @Provides
    fun providesWifiHelper() = wifiHelper

    @Provides
    @Named("url")
    fun provideUrlLocalStorage() = urlStorage

    @Provides
    @Named("session")
    fun provideSessionLocalStorage() = sessionLocalStorage

    @Provides
    @Named("integration")
    fun provideIntegrationLocalStorage() = integrationLocalStorage

    @Provides
    @Named("manufacturer")
    fun provideDeviceManufacturer() = Build.MANUFACTURER

    @Provides
    @Named("model")
    fun provideDeviceModel() = Build.MODEL

    @Provides
    @Named("osVersion")
    fun provideDeviceOsVersion() = Build.VERSION.SDK_INT.toString()

    @Module
    interface Declaration {
        @Binds
        fun bindUrlRepositoryImpl(repository: UrlRepositoryImpl): UrlRepository

        @Binds
        fun bindAuthenticationRepositoryImpl(repository: AuthenticationRepositoryImpl): AuthenticationRepository

        @Binds
        fun bindIntegrationService(repository: IntegrationRepositoryImpl): IntegrationRepository
    }
}
